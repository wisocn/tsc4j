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

package com.github.tsc4j.micronaut2;

import com.github.tsc4j.api.Reloadable;
import com.github.tsc4j.api.ReloadableConfig;
import com.github.tsc4j.core.AtomicInstance;
import com.github.tsc4j.core.CloseableInstance;
import com.github.tsc4j.core.CloseableReloadableConfig;
import com.github.tsc4j.core.Tsc4j;
import com.github.tsc4j.core.Tsc4jImplUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.micronaut.context.env.PropertySource;
import lombok.NonNull;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Micronaut tsc4j property source implementation.
 */
@Slf4j
public final class Tsc4jPropertySource extends CloseableInstance implements PropertySource {
    private final AtomicInstance<CloseableReloadableConfig> rcHolder =
        new AtomicInstance<>(CloseableReloadableConfig::close);

    private final Reloadable<Config> reloadable;

    /**
     * Currently assigned config.
     *
     * @see #propertyNames
     */
    private volatile Config config = ConfigFactory.empty();

    /**
     * Cached {@link #config} property names.
     */
    private volatile List<String> propertyNames = Collections.emptyList();

    /**
     * Creates new instance.
     *
     * @param reloadableConfig reloadable config
     */
    @Inject
    public Tsc4jPropertySource(@NonNull ReloadableConfig reloadableConfig) {
        this(reloadableConfig.register(Function.identity()));
    }

    /**
     * Creates new instance.
     *
     * @param reloadable reloadable of entire config
     */
    protected Tsc4jPropertySource(@NonNull Reloadable<Config> reloadable) {
        this.reloadable = Objects.requireNonNull(reloadable.ifPresentAndRegister(this::updateConfig),
            "Reloadable ifPresentAndRegister() should not return null!");
        log.debug("created micronaut property source: {}", this);
    }

    @Synchronized
    private void updateConfig(Config config) {
        if (config == null) {
            log.warn("configuration disappeared, retaining current config.");
            return;
        }

        this.config = config;
        this.propertyNames = config.entrySet().stream()
            .map(Map.Entry::getKey)
            .sorted()
            .collect(Collectors.toList());

        log.debug("assigned new config: {}", config.hashCode());
        log.debug("new assigned config property names : {}", propertyNames);
    }

    @Override
    public PropertyConvention getConvention() {
        return PropertyConvention.JAVA_PROPERTIES;
    }

    @Override
    public int getOrder() {
        return Tsc4jPropertySourceLoader.ORDER_POSITION;
    }

    @Override
    public String getName() {
        return Tsc4jImplUtils.NAME;
    }

    @Override
    public Object get(@NonNull String key) {
        val path = Tsc4j.configPath(key);
        if (!path.isEmpty() && config.hasPath(path)) {
            val value = config.getValue(path).unwrapped();
            if (log.isTraceEnabled()) {
                log.trace("returning value for config property '{}': {}", key, value);
            } else if (log.isDebugEnabled()) {
                log.debug("returning value for config property '{}'", key);
            }
            return config.getValue(path).unwrapped();
        }

        log.debug("missing tsc4j config property: {}", key);
        return null;
    }

    @Override
    public Iterator<String> iterator() {
        log.debug("asked for property names.");
        return propertyNames.iterator();
    }

    @Override
    protected void doClose() {
        reloadable.close();
    }

    @Override
    public String toString() {
        return Tsc4jImplUtils.NAME;
    }
}
