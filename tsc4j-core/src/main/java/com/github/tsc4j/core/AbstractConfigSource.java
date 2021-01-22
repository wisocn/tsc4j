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

package com.github.tsc4j.core;

import com.github.tsc4j.core.impl.Stopwatch;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.val;

import java.io.InputStream;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Base class for writing {@link ConfigSource} aliases.
 */
public abstract class AbstractConfigSource extends BaseInstance implements ConfigSource {
    /**
     * Sub-directory with special meaning - it will be always listed if resolved configuration path is directory
     * and that directory contains sub directory: <b>{@value}</b>
     */
    protected static final String CONF_D_DIR = "conf.d";

    private static final Pattern PATH_SANITATION_PATTERN = Pattern.compile("/{2,}");
    private static final Set<String> CONFIG_BASENAMES = createBasenames();

    /**
     * Default environment list.
     */
    protected static final List<String> DEFAULT_ENVS = Collections.singletonList(Tsc4jImplUtils.DEFAULT_ENV_NAME);

    private final boolean allowErrors;

    /**
     * Tells whether warning should be logged when config source will try to open/list non-existing paths.
     *
     * @see #isFailOnMissing()
     */
    @Getter(AccessLevel.PROTECTED)
    private final boolean warnOnMissing;

    /**
     * Tells whether fetch error is issued when any of configuration location do not exist. This method takes
     * precedence to {@link #isWarnOnMissing()}.
     */
    @Getter(AccessLevel.PROTECTED)
    private final boolean failOnMissing;

    /**
     * Tells whether operations should be done in parallel if possible.
     */
    @Getter(AccessLevel.PROTECTED)
    private final boolean parallel;

    private final AtomicBoolean firstFetch = new AtomicBoolean(true);
    private final Set<String> alreadyWarnedMissingLocations = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Creates new instance.
     *
     * @param builder instance builder
     * @throws IllegalStateException in case of bad builder state
     */
    protected AbstractConfigSource(@NonNull ConfigSourceBuilder builder) {
        super(builder.getName());

        builder.checkState();

        this.allowErrors = builder.isAllowErrors();
        this.warnOnMissing = builder.isWarnOnMissing();
        this.failOnMissing = builder.isFailOnMissing();
        this.parallel = builder.isParallel();
    }

    @Override
    public boolean allowErrors() {
        return allowErrors;
    }

    @Override
    public Config get(@NonNull ConfigQuery query) throws RuntimeException {
        if (firstFetch.compareAndSet(true, false)) {
            onFirstFetch();
        }

        val sw = new Stopwatch();
        val config = fetchConfigs(query).stream()
            .filter(Objects::nonNull)
            .filter(e -> !e.isEmpty())
            .reduce(ConfigFactory.empty(), (previous, current) -> current.withFallback(previous));
        log.debug("{} loaded configuration in {}", this, sw);

        return debugLoadedConfig("", config);
    }

    /**
     * Method supposed to be fired on first fetch by the actual aliases.
     */
    protected void onFirstFetch() {
    }

    /**
     * Loads configuration from {@code byte[]} source.
     *
     * @param configBytes configuration stored in byte array.
     * @param origin      config origin, name, path, see {@link com.typesafe.config.ConfigOrigin}
     * @return config instance.
     * @throws NullPointerException                in case of null arguments
     * @throws com.typesafe.config.ConfigException when configuration cannot be loaded or parsed from the source
     * @see Config#origin()
     */
    protected final Config readConfig(@NonNull byte[] configBytes, @NonNull String origin) {
        return Tsc4jImplUtils.readConfig(configBytes, origin);
    }

    /**
     * Loads configuration from {@link String} source.
     *
     * @param configString input stream to read config from
     * @param origin       config origin, name, path, see {@link com.typesafe.config.ConfigOrigin}
     * @return config instance.
     * @throws NullPointerException                in case of null arguments
     * @throws com.typesafe.config.ConfigException when configuration cannot be loaded or parsed from the source
     * @see Config#origin()
     */
    protected final Config readConfig(@NonNull String configString, @NonNull String origin) {
        return Tsc4jImplUtils.readConfig(configString, origin);
    }

    /**
     * Loads configuration from {@link InputStream} source; {@code inputStream} is automatically closed after
     * configuration is parsed.
     *
     * @param inputStream input stream to read config from
     * @param origin      config origin, name, path, see {@link com.typesafe.config.ConfigOrigin}
     * @return config instance.
     * @throws NullPointerException                in case of null arguments
     * @throws com.typesafe.config.ConfigException when configuration cannot be loaded or parsed from the source
     * @see Config#origin()
     */
    protected final Config readConfig(@NonNull InputStream inputStream, @NonNull String origin) {
        return Tsc4jImplUtils.readConfig(inputStream, origin);
    }

    /**
     * Loads configuration from {@link Reader} source; {@code reader} is automatically closed after
     * configuration is parsed.
     *
     * @param reader reader to read from
     * @param origin config origin, name, path, see {@link com.typesafe.config.ConfigOrigin}
     * @return config instance.
     * @throws NullPointerException                in case of null arguments
     * @throws com.typesafe.config.ConfigException when configuration cannot be loaded or parsed from the source
     * @see Config#origin()
     */
    protected final Config readConfig(@NonNull Reader reader, @NonNull String origin) {
        return Tsc4jImplUtils.readConfig(reader, origin);
    }

    /**
     * Returns unique, non-empty environment names from config query.
     *
     * @param query config query.
     * @return sanitized envs from config query, or {@link #DEFAULT_ENVS} if query doesn't contain valid environment
     *     names.
     */
    protected final List<String> getEnvsFromConfigQuery(@NonNull ConfigQuery query) {
        List<String> envs = query.getEnvs();
        if (envs == null) {
            return DEFAULT_ENVS;
        }
        envs = Tsc4jImplUtils.sanitizeEnvs(envs);
        return envs.isEmpty() ? DEFAULT_ENVS : envs;
    }

    /**
     * Interpolates string containing config query variables.
     * <p/>
     * Implemented variable names:
     * <ul>
     * <li>{@code ${application}}: application name</li>
     * <li>{@code ${datacenter}}: datacenter name</li>
     * <li>{@code ${env}}: environment name</li>
     * </ul>
     * <p/>
     * Interpolation is applied on each element on {@code strings} based on {@code query.} {@link
     * ConfigQuery#getEnvs()}, pseudocode:
     * <pre>
     * {@code
     * result = []
     * for envName from config.getEnvs(); do
     *   result += interpolate(envName, strings, query)
     * done
     * }
     * </pre>
     *
     * @param strings strings to interpolate
     * @param query   config query
     * @return stream of unique interpolated strings with query variables resolved.
     * @see #interpolateWithEnv(String, String, ConfigQuery)
     */
    protected final List<String> interpolateVarStrings(@NonNull Collection<String> strings,
                                                       @NonNull ConfigQuery query) {
        return getEnvsFromConfigQuery(query).stream()
            .flatMap(envName -> interpolateVarStringsForEnv(envName, strings, query))
            .filter(e -> !e.isEmpty())
            .distinct()
            .collect(Collectors.toList());
    }

    private Stream<String> interpolateVarStringsForEnv(@NonNull String envName,
                                                       @NonNull Collection<String> strings,
                                                       @NonNull ConfigQuery query) {
        return strings.stream()
            .filter(Objects::nonNull)
            .map(str -> interpolateWithEnv(str, envName, query));
    }

    /**
     * Replaces environment name placeholders {@code ${env} or $env} in specified string.<p/>
     * Implemented variable names:
     * <ul>
     * <li>{@code ${application}}: application name</li>
     * <li>{@code ${datacenter}}: datacenter name</li>
     * <li>{@code ${env}}: environment name</li>
     *
     * @param
     * @param str     string to interpolate.
     * @param envName environment name
     * @param query   config query
     * @return interpolated string (variables replaced with values from {@code envName} and {@code query})
     */
    private String interpolateWithEnv(@NonNull String str, @NonNull String envName, @NonNull ConfigQuery query) {
        if (str == null) {
            return "";
        }
        return str
            .replace("${env}", envName)
            .replace("$env", envName)
            .replace("${application}", query.getAppName())
            .replace("$application", query.getAppName())
            .replace("${datacenter}", query.getDatacenter())
            .replace("$datacenter", query.getDatacenter());
    }

    /**
     * Fetches configuration(s) for specified configuration query.
     *
     * @param query configuration query object.
     * @return stream of configuration(s) for specified environment
     * @throws RuntimeException in case of fatal fetch error (access denied for example)
     */
    protected abstract List<Config> fetchConfigs(@NonNull ConfigQuery query);

    protected boolean isValidConfigName(@NonNull String path) {
        val basename = basename(path);
        if (CONFIG_BASENAMES.contains(basename)) {
            return true;
        }
        if (path.endsWith("/" + CONF_D_DIR + "/" + basename)) {
            return path.endsWith(".conf") || path.endsWith(".json") || path.endsWith(".properties");
        }
        return false;
    }

    /**
     * Sanitizes paths.
     *
     * @param paths collection of paths
     * @return sanitized path list
     * @see #removeDoubleSlashesFromPath(String)
     */
    protected final List<String> sanitizePaths(@NonNull Collection<String> paths) {
        val list = Tsc4jImplUtils.uniqStream(paths)
            .map(this::removeDoubleSlashesFromPath)
            .distinct()
            .collect(Collectors.toList());
        return Collections.unmodifiableList(list);
    }

    /**
     * Removes double-slashes from the path.
     *
     * @param path path
     * @return sanitized path
     */
    protected final String removeDoubleSlashesFromPath(@NonNull String path) {
        return PATH_SANITATION_PATTERN.matcher(path).replaceAll("/");
    }

    /**
     * Returns basename(3) of the path.
     *
     * @param path path
     * @return path basename
     * @throws NullPointerException in case of null arguments
     */
    protected final String basename(@NonNull String path) {
        path = removeDoubleSlashesFromPath(path);
        val idx = path.lastIndexOf('/');
        if (idx < 0) {
            return path;
        }
        if (path.length() > (idx + 1)) {
            return path.substring(idx + 1);
        } else {
            return path.substring(0, idx);
        }
    }

    /**
     * Warns or throws exception in case of missing config resource location.
     *
     * @param location config resource location
     * @throws if {@link #isFailOnMissing()} is {@code true}
     * @see #isFailOnMissing()
     * @see #isWarnOnMissing()
     */
    protected final void warnOrThrowOnMissingConfigLocation(@NonNull String location) {
        possiblyThrowOnMissingConfigLocation(location);
        warnOnMissingConfigLocation(location);
    }

    private void possiblyThrowOnMissingConfigLocation(@NonNull String location) {
        if (isFailOnMissing()) {
            throw new IllegalArgumentException("Config location does not exist: " + location);
        }
    }

    private void warnOnMissingConfigLocation(@NonNull String location) {
        val msg = "{} config location does not exist: {}";
        if (isWarnOnMissing()) {
            if (!alreadyWarnedAboutMissingLocation(location)) {
                log.warn(msg, this, location);
            }
        } else {
            log.debug(msg, this, location);
        }
    }

    private boolean alreadyWarnedAboutMissingLocation(String path) {
        return !alreadyWarnedMissingLocations.add(path);
    }

    /**
     * Logs loaded config at appropriate debug/trace log level.
     *
     * @param path   path from which config was loaded
     * @param config loaded config
     * @return provided {@code config} instance
     */
    protected final Config debugLoadedConfig(String path, @NonNull Config config) {
        if (log.isTraceEnabled()) {
            val pathMsg = (path != null && !path.isEmpty()) ? "from '" + path + "' " : "";
            log.trace("{} loaded config {}(resolved: {}): {}", this, pathMsg, config.isResolved(), config);
        }
        return config;
    }


    private static Set<String> createBasenames() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "application.conf",
            "application.json",
            "application.properties")));
    }
}
