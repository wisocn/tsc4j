/*
 * Copyright 2017 - 2021 tsc4j project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.tsc4j.spring;

import com.github.tsc4j.core.AtomicInstance;
import com.github.tsc4j.core.CloseableReloadableConfig;
import com.github.tsc4j.core.ReloadableConfigFactory;
import com.github.tsc4j.core.Tsc4j;
import com.github.tsc4j.core.Tsc4jImplUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Various spring-related utilities.
 */
@Slf4j
@UtilityClass
class SpringUtils {
    /**
     * @see #getPropertyNameListIndex(String)
     */
    private static final Pattern PROPERTY_LIST_PATTERN = Pattern.compile("\\[(\\d+)\\]$");

    /**
     * {@link com.github.tsc4j.api.ReloadableConfig} instance holder.
     */
    private static final AtomicInstance<CloseableReloadableConfig> instanceHolder =
        new AtomicInstance<>(CloseableReloadableConfig::close);

    /**
     * Translates active spring profiles from the environment to unique list of tsc4j env names.
     *
     * @param env spring environment
     * @return list of tsc4j application environments.
     */
    List<String> getTsc4jEnvs(@NonNull Environment env) {
        List<String> profiles = Arrays.asList(env.getActiveProfiles());
        if (profiles.isEmpty()) {
            log.debug("spring environment doesn't declare active profiles, using default profiles.");
            profiles = Arrays.asList(env.getDefaultProfiles());
        }
        profiles = Tsc4jImplUtils.toUniqueList(profiles);
        if (profiles.isEmpty()) {
            throw new IllegalStateException("No spring application profiles are defined.");
        }
        return Collections.unmodifiableList(profiles);
    }

    private CloseableReloadableConfig createReloadableConfig(@NonNull String appName, @NonNull Collection<String> envs) {
        // create rc, but spring doesn't have notion of running datacenter name
        val rc = ReloadableConfigFactory.defaults()
            .setAppName(appName)
            .setEnvs(new ArrayList<>(envs))
            .create();
        log.debug("created reloadable config instance: {}", rc);
        val config = rc.getSync();
        log.trace("fetched initial config: {}", config);

        return rc;
    }

    /**
     * Atomic {@link com.github.tsc4j.api.ReloadableConfig} instance holder.
     *
     * @return atomic instance.
     */
    AtomicInstance<CloseableReloadableConfig> instanceHolder() {
        return instanceHolder;
    }

    /**
     * Clears stored reloadable config.
     *
     * @see #reloadableConfig(String, Collection)
     */
    void clearReloadableConfig() {
        instanceHolder().clear();
    }

    /**
     * Retrieves existing {@link com.github.tsc4j.api.ReloadableConfig} instance if exists, otherwise creates it.
     *
     * @param env spring environment
     * @return reloadable config
     */
    CloseableReloadableConfig reloadableConfig(@NonNull Environment env) {
        return reloadableConfig(getAppName(env), getTsc4jEnvs(env));
    }

    /**
     * Retrieves existing {@link com.github.tsc4j.api.ReloadableConfig} instance if exists, otherwise creates it.
     *
     * @param appName application name
     * @param envs    application envs
     * @return reloadable config
     * @see #clearReloadableConfig()
     */
    CloseableReloadableConfig reloadableConfig(@NonNull String appName, @NonNull Collection<String> envs) {
        return instanceHolder().getOrCreate(() -> createReloadableConfig(appName, envs));
    }

    /**
     * Returns string with all properties found in all property sources registered in given environment <b>except</b>
     * system properties and environment variables.
     *
     * @param env environment
     * @return property source info as a string
     */
    String debugPropertySources(@NonNull ConfigurableEnvironment env) {
        return env.getPropertySources().stream()
            .filter(it -> !it.getName().equals("systemProperties"))
            .filter(it -> !it.getName().equals("systemEnvironment"))
            .map(SpringUtils::debugPropertySource)
            .collect(Collectors.joining("\n"));
    }

    private String debugPropertySource(PropertySource<?> it) {
        val sb = new StringBuilder();
        sb.append("PROPERTY SOURCE: " + it.getName() + " [" + it.getClass().getName() + "]\n");
        if (it instanceof EnumerablePropertySource) {
            val ms = Stream.of(((EnumerablePropertySource<?>) it).getPropertyNames())
                .sorted()
                .map(x -> formatPropValue(it, x))
                .collect(Collectors.joining("\n  "));
            sb.append("  " + ms);
        }
        return sb.toString();
    }

    private String formatPropValue(PropertySource<?> propertySource, String propName) {
        val value = propertySource.getProperty(propName);
        if (value == null) {
            return "";
        }
        return String.format("%-40.40s %-20.20s  %s", propName, value, value.getClass().getName());
    }

    /**
     * Converts given config instance to a map of spring property map.
     *
     * @param config config to convert
     * @return given config instance as a immutable map of spring property names
     */
    Map<String, Object> toSpringPropertyMap(@NonNull Config config) {
        if (config.isEmpty()) {
            return Collections.emptyMap();
        }

        val propNames = propertyNames(config, false);

        // construct value map
        val map = new LinkedHashMap<String, Object>();
        propNames.forEach(key -> map.put(key, springPropertyValue(key, config)));

        return Collections.unmodifiableMap(map);
    }

    private Set<String> propertyNames(@NonNull Config config, boolean appendList) {
        val paths = config.entrySet().stream()
            .flatMap(e -> propertyNameStream(e, appendList))
            .sorted()
            .collect(Collectors.toCollection(LinkedHashSet::new));
        log.debug("config property names from current config: {}", paths);
        return Collections.unmodifiableSet(paths);
    }

    private Stream<String> propertyNameStream(Map.Entry<String, ConfigValue> e, boolean appendList) {
        val value = e.getValue();
        if (value.valueType() == ConfigValueType.LIST) {
            val size = ((List) value.unwrapped()).size();
            val resultStream = IntStream.range(0, size)
                .mapToObj(idx -> e.getKey() + "[" + idx + "]");

            return appendList ? Stream.concat(Stream.of(e.getKey()), resultStream) : resultStream;
        } else {
            return Stream.of(e.getKey());
        }
    }

    private Object springPropertyValue(String name, Config config) {
        log.debug("springPropertyValue('{}')", name);
        val key = sanitizeSpringPropertyKey(name);

        if (config.hasPathOrNull(key)) {
            val value = config.getAnyRef(key);
            val result = doGetResult(name, value);
            log.trace("springPropertyValue('{}'/'{}'): `{}` => `{}`", name, key, value, result);
            return result;
        }

        log.trace("springPropertyValue('{}'/'{}'): {}", name, key, null);
        return null;
    }


    private String sanitizeSpringPropertyKey(String key) {
        val withoutDefaultValue = removeDefaultValueFromPropertyName(key).trim();
        return getPathWithoutSquareBrackets(withoutDefaultValue).trim();
    }

    String getDefaultValueFromPropertyName(String name) {
        val startIdx = name.lastIndexOf(':') + 1;
        if (startIdx >= 1 && startIdx <= name.length()) {
            return name.substring(startIdx);
        }
        return "";
    }

    String removeDefaultValueFromPropertyName(String key) {
        val idx = key.lastIndexOf(':');
        return (idx < 0) ? key : key.substring(0, idx);
    }

    private String getPathWithoutSquareBrackets(String key) {
        val idx = key.indexOf('[');
        val realName = (idx < 0) ? key : key.substring(0, idx);
        return Tsc4j.configPath(realName);
    }

    private Object doGetResult(String name, Object value) {
        log.debug("  doGetResult: {} FROM {}, list: {}", name, value, (value instanceof List));
        Object r = null;

        if (value != null) {
            if (value instanceof List) {
                val list = (List) value;
                val idx = getPropertyNameListIndex(name);
                if (idx >= 0 && idx < list.size()) {
                    r = list.get(idx);
                } else {
                    r = null;
                }
            } else {
                r = value;
            }
        }

        if (r == null) {
            r = "";
        }

        log.debug("  doGetResult '{}' => '{}'", name, r);
        return r.toString();
    }

    private int getPropertyNameListIndex(String name) {
        val matcher = PROPERTY_LIST_PATTERN.matcher(name);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return -1;
    }

    /**
     * Returns application name obtained from a given environment.
     *
     * @param env spring environment
     * @return application name
     * @throws IllegalStateException if application name can't be determined
     */
    String getAppName(@NonNull Environment env) {
        return Tsc4jImplUtils.optString(env.getProperty("spring.application.name"))
            .orElseThrow(() -> new IllegalStateException("Can't determine application name from spring environment"));
    }
}
