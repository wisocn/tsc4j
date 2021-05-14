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
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.EnumerablePropertySource;

import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Tsc4j {@link org.springframework.core.env.PropertySource} implementation.
 */
@Slf4j
@Order
class Tsc4jPropertySource extends EnumerablePropertySource<ReloadableConfig> implements Closeable, InitializingBean {
    // reloadable that gets notified when config changes
    private final Reloadable<Config> reloadable;

    /**
     * Current config values
     */
    private volatile Config currentConfig = null;
    private volatile Set<String> currentPropertyNames;

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

    @Synchronized
    private void updateCurrentConfig(Config config) {
        if (config == null) {
            log.warn("configuration disappeared, retaining existing config.");
            return;
        }

        this.currentPropertyNames = Tsc4jImplUtils.propertyNames(config);
        this.currentConfig = config;

        if (log.isDebugEnabled()) {
            log.trace("{} assigned new config object: {}", this, config);
            log.debug("{} assigned new property names ({}): {}",
                this, currentPropertyNames.size(), currentPropertyNames);
        }
    }

    @Override
    public boolean containsProperty(@NonNull String name) {
        waitForConfigFetch();
        val idx = name.indexOf(':');
        if (idx > 0) {
            name = name.substring(0, idx);
        }

        val cleanName = (idx > 0) ? name.substring(0, idx) : name;

        val result = currentPropertyNames.contains(cleanName);
        log.debug("{} containsProperty() '{}/{}': {}", this, name, cleanName, result);
        return result;
    }

    @Override
    public String[] getPropertyNames() {
        waitForConfigFetch();
        val names = currentPropertyNames;
        val result = names.toArray(new String[0]);
        if (log.isDebugEnabled()) {
            log.debug("{} retrieving current property name array ({} entries)", this, names.size());
            log.trace("{} retrieving current property names: {}", this, names);
        }
        return result;
    }

    @Override
    public Object getProperty(@NonNull String name) {
        val config = waitForConfigFetch();
        val result = Tsc4jImplUtils.getPropertyFromConfig(name, config);
        return (result != null) ? result.toString() : null;
    }

    @PreDestroy
    @Override
    public void close() {
        log.warn("closing {}@{}", getClass().getName(), hashCode());

        log.debug("{} closing property source.", this);
        reloadable.close();
    }

    /**
     * Waits for configuration to be fetched for the first time.
     *
     * @return currently assigned config
     * @throws IllegalStateException if config cannot be fetched.
     * @see #updateCurrentConfig(Config)
     */
    private Config waitForConfigFetch() {
        val config = this.currentConfig;
        if (config == null) {
            return fetchConfig();
        }
        return config;
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

    @Override
    public void afterPropertiesSet() {
        log.debug("afterPropertiesSet() invoked.");
    }
}
