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

package com.github.tsc4j.core.impl;


import com.github.tsc4j.api.Reloadable;
import com.github.tsc4j.core.Tsc4j;
import com.github.tsc4j.core.Tsc4jImplUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigMergeable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Default implementation of {@link Reloadable}.
 *
 * @param <T> value type
 */
@Slf4j
@EqualsAndHashCode(of = {"id", "path", "converter"}, callSuper = false)
public final class DefaultReloadable<T> extends AbstractReloadable<T> implements Comparable<DefaultReloadable<?>> {
    /**
     * Reloadable id.
     */
    @Getter
    private final long id;

    /**
     * Configuration path if defined, otherwise empty string.
     */
    @Getter
    private final String path;

    /**
     * Function that converts {@link ConfigMergeable} to actual type, never null.
     */
    private final Function<Config, T> converter;

    /**
     * Consumer that is invoked on close.
     *
     * @see #close()
     */
    private volatile Consumer<DefaultReloadable> closeConsumer;

    private final AtomicReference<String> checksumRef = new AtomicReference<>();

    /**
     * Creates new instance.
     *
     * @param id        reloadable id
     * @param path      configuration path
     * @param converter configuration function converter
     * @throws NullPointerException if converter is null.
     */
    public DefaultReloadable(long id,
                             @NonNull String path,
                             @NonNull Function<Config, T> converter,
                             @NonNull Consumer<DefaultReloadable> closeConsumer) {
        this.id = id;
        this.path = Tsc4j.configPath(path);
        this.converter = converter;
        this.closeConsumer = closeConsumer;
    }

    void accept(@NonNull Config config) {
        val newConfigChecksum = configChecksum(config);
        val checksumDiffers = !newConfigChecksum.equals(checksumRef.get());
        log.debug("config checksum differs: {}, new '{}', old '{}'", checksumDiffers, newConfigChecksum, checksumRef.get());
        if (checksumDiffers) {
            applyNewConfig(config, newConfigChecksum);
        }
    }

    @Override
    public int compareTo(DefaultReloadable<?> o) {
        if (o == null) {
            return 1;
        }

        val byPath = getPath().compareTo(o.getPath());
        if (byPath != 0) {
            return byPath;
        }

        return Long.compare(getId(), o.getId());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
            "(id=" + getId() + ", present=" + isPresent() + ", updates=" + getNumUpdates() +
            ", path=" + getPath() + ")";
    }

    /**
     * Applies updated config and extracts the value.
     *
     * @param config         newly fetched configuration
     * @param configChecksum checksum
     */
    private void applyNewConfig(@NonNull Config config, @NonNull String configChecksum) {
        if (!hasConfigPath(config)) {
            log.debug("configuration doesn't have value at path {}", getPath());
            removeValue();
            checksumRef.set(configChecksum);
            return;
        }

        val value = extractValue(config);
        if (value == null) {
            log.warn("converter {} returned null value from config object: {}", converter, config);
            return;
        }

        setValue(value);
        checksumRef.set(configChecksum);
    }

    private T extractValue(Config config) {
        return converter.apply(config);
    }

    /**
     * Computes config checksum according to value of {@link #getPath()}.
     *
     * @param config entire loaded config instance
     * @return configuration checksum as string at path {@link #getPath()}.
     */
    private String configChecksum(@NonNull Config config) {
        return getConfigValue(config).map(Tsc4jImplUtils::objectChecksum).orElse("");
    }

    /**
     * Tells whether specified {@link Config} instance contains non-null path returned by {@link #getPath()}.
     *
     * @param config configuration object
     * @return true/false
     */
    private boolean hasConfigPath(@NonNull Config config) {
        if (path.isEmpty()) {
            return true;
        } else if (!config.hasPath(path)) {
            log.debug("non-existent config path: '{}'", path);
            return false;
        }
        return true;
    }

    private Optional<ConfigMergeable> getConfigValue(@NonNull Config config) {
        if (path.isEmpty()) {
            log.debug("getConfigValue(): path is empty, returning entire config.");
            return Optional.of(config);
        } else if (!hasConfigPath(config)) {
            log.debug("getConfigValue() config doesn't contain path: {}", path);
            return Optional.empty();
        }

        // fetch value
        return Optional.ofNullable(config.getValue(path));
    }

    @Override
    protected void doClose() {
        super.doClose();

        val consumer = this.closeConsumer;
        if (consumer != null) {
            this.closeConsumer = null;
            Tsc4jImplUtils.safeRunnable(() -> consumer.accept(this)).run();
        }
    }
}
