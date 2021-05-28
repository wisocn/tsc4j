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

import com.github.tsc4j.api.Reloadable;
import com.github.tsc4j.api.ReloadableConfig;
import com.github.tsc4j.core.Tsc4jImplUtils;
import com.typesafe.config.Config;
import lombok.NonNull;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.env.EnumerablePropertySource;

import java.io.Closeable;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tsc4j {@link org.springframework.core.env.PropertySource} implementation.
 */
@Slf4j
final class Tsc4jPropertySource extends EnumerablePropertySource<ReloadableConfig> implements Closeable, Ordered {
    // reloadable that gets notified when config changes
    private final Reloadable<Config> reloadable;

    /**
     * Current spring property map
     */
    private volatile Map<String, Object> springPropertyMap = null;

    /**
     * Creates new instance with source name <b>{@value Tsc4jImplUtils#NAME}</b>.
     *
     * @param reloadableConfig reloadable config
     * @throws NullPointerException in case of null arguments
     * @see Tsc4jImplUtils#NAME
     */
    @Autowired
    public Tsc4jPropertySource(@NonNull ReloadableConfig reloadableConfig) {
        this(Tsc4jImplUtils.NAME, reloadableConfig);
    }

    /**
     * Creates new instance.
     *
     * @param name             property source name
     * @param reloadableConfig reloadable config
     * @throws NullPointerException in case of null arguments
     */
    public Tsc4jPropertySource(@NonNull String name, @NonNull ReloadableConfig reloadableConfig) {
        super(name, reloadableConfig);
        this.reloadable = reloadableConfig
            .register(Function.identity())
            .ifPresentAndRegister(this::updateCurrentConfig);
        log.debug("created tsc4j spring property source: {}", this);
    }

    private void updateCurrentConfig(Config config) {
        if (config == null) {
            log.debug("cleared current config value map, because configuration has disappeared.");
            return;
        }

        assignPropertyMap(config);
    }

    @Override
    public boolean containsProperty(@NonNull String name) {
        val result = waitForConfigFetch().containsKey(name);
        log.debug("containsProperty(): {} => {}", name, result);
        return result;
    }

    @Override
    public String[] getPropertyNames() {
        val map = waitForConfigFetch();
        val keys = map.keySet();
        val result = keys.toArray(new String[0]);

        log.debug("getPropertyNames(): {}", keys);
        return result;
    }

    @Override
    public Object getProperty(@NonNull String name) {
        val map = waitForConfigFetch();
        val key = SpringUtils.removeDefaultValueFromPropertyName(name);
        val defaultValue = SpringUtils.getDefaultValueFromPropertyName(name);

        Object result = map.get(key);
        if (result == null && !defaultValue.isEmpty()) {
            result = defaultValue;
        }

        log.trace("getProperty(): `{}` (def: `{}`) => `{}`", name, defaultValue, result);
        return result;
    }

    //@PreDestroy
    @Override
    public void close() {
        log.debug("{} closing property source.", this);
        reloadable.close();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * Waits for configuration to be fetched for the first time.
     *
     * @return currently assigned config
     * @throws IllegalStateException if config cannot be fetched.
     * @see #updateCurrentConfig(Config)
     */
    private Map<String, Object> waitForConfigFetch() {
        val m = this.springPropertyMap;
        if (m == null) {
            return assignPropertyMap(fetchConfig());
        }

        return m;
    }

    /**
     * Fetches fresh config.
     *
     * @return current config
     * @throws IllegalStateException if source returned null config
     * @throws RuntimeException      if configuration cannot be fetched
     */
    private Config fetchConfig() {
        return Optional.ofNullable(getSource().getSync())
            .orElseThrow(() -> new IllegalStateException("Source returned null config."));
    }

    private Map<String, Object> assignPropertyMap(Config config) {
        return assignPropertyMap(SpringUtils.toSpringPropertyMap(config));
    }

    @Synchronized
    private Map<String, Object> assignPropertyMap(@NonNull Map<String, Object> map) {
        // assign
        this.springPropertyMap = map;

        if (log.isDebugEnabled()) {
            val str = springPropertyMap.entrySet().stream()
                .map(it -> "  " + it.getKey() + " => " + it.getValue().getClass().getName() + " `" + it.getValue() + "`")
                .collect(Collectors.joining("\n"));
            log.trace("updated current config properties:\n{}", str);
        }

        return this.springPropertyMap;
    }
}
