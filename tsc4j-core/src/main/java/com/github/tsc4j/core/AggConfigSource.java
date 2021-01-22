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
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.Closeable;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * <p>{@link ConfigSource} that aggregates configs fetched from one or more encapsulated config sources.</p>
 * <p>
 * Configuration fetch order is as follows:
 * <ul>
 * <li>{@link #getOverrideSupplier()}</li>
 * <li>each supplier from {@link #getSources()}</li>
 * <li>{@link #getFallbackSupplier()}</li>
 * </ul>
 * <p>
 * Configurations are resolved in the same order, where each following {@link Config} instance overrides/adds values
 * of previous one.
 */
@Slf4j
@Value
@Builder
@EqualsAndHashCode(callSuper = false)
public final class AggConfigSource extends CloseableInstance implements ConfigSource {
    /**
     * Configuration supplier that provides {@link Config} instance that overrides all other fetched configs. Default
     * is
     * {@link ConfigFactory#empty()}.
     */
    @Getter(AccessLevel.PROTECTED)
    @NonNull
    @Builder.Default
    Supplier<Config> overrideSupplier = ConfigFactory::empty;

    /**
     * Supplier that provides {@link Config} instance that is used as fallback for previously fetched configs.
     * Default is {@link ConfigFactory#load()}.
     *
     * @see Config#resolve()
     */
    @Getter(AccessLevel.PROTECTED)
    @NonNull
    @Builder.Default
    Supplier<Config> fallbackSupplier = ConfigFactory::empty;

    /**
     * List of configuration sources.
     */
    @Getter(AccessLevel.PROTECTED)
    @NonNull
    @Singular("source")
    List<ConfigSource> sources;

    @Override
    public boolean allowErrors() {
        return false;
    }

    /**
     * Fetches and merges configurations from all encapsulated configuration suppliers and returns merged and resolved
     * configuration.
     *
     * @param query config query
     * @return configuration
     * @throws RuntimeException if any of underlying configuration suppliers (including default supplier)
     *                          throws exception or if configuration cannot be resolved.
     * @see #getOverrideSupplier()
     * @see #getSources()
     * @see #getFallbackSupplier()
     * @see Config#isResolved()
     */
    @Override
    public Config get(@NonNull ConfigQuery query) {
        checkClosed();

        val sw = new Stopwatch();
        try {
            val config = doGet(query);
            log.debug("{} config fetch succeeded after {}", this, sw);
            return config;
        } catch (Throwable t) {
            log.debug("{} config fetch failed after: {}", this, sw);
            throw Tsc4jImplUtils.toRuntimeException(t);
        }
    }

    private Config doGet(@NonNull ConfigQuery query) {
        // fetch override config
        val overrideConfig = fetchConfig(overrideSupplier).orElse(ConfigFactory.empty());

        // fetch configs from all normal config sources and merge them in one
        val mergedConfig = fetchConfigs(query).stream()
            .reduce(ConfigFactory.empty(), (previous, current) -> current.withFallback(previous));

        // fetch fallback config and merge it with current config
        val configWithFallback = fetchConfig(fallbackSupplier)
            .map(cfg -> {
                log.trace("fallback supplier config: {}", cfg);
                return mergedConfig.withFallback(cfg);
            })
            .orElse(mergedConfig);

        // apply override config
        val finalConfig = overrideConfig.withFallback(configWithFallback);
        log.trace("config before resolving (resolved: {}): {}", finalConfig.isResolved(), finalConfig);

        // moment of truth, resolve configuration
        val resolvedConfig = finalConfig.resolve();
        log.trace("resolved config: {}", resolvedConfig);

        return Tsc4j.withoutSystemPropertiesAndEnvVars(resolvedConfig);
    }


    /**
     * Fetches configs from all registered configuration suppliers.
     *
     * @param query config query
     * @return list of fetched configs
     * @throws NullPointerException in case of null arguments
     * @throws RuntimeException     when any config supplier that doesn't allow fetching errors throws.
     * @see #sources
     * @see #fetchConfig(Supplier)
     * @see ConfigSource#allowErrors()
     */
    protected List<Config> fetchConfigs(@NonNull ConfigQuery query) {
        val list = sources.stream()
            .map(source -> fetchConfig(source, query))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());

        if (log.isDebugEnabled()) {
            log.debug("fetched {} config(s).", list.size());
            val counter = new AtomicInteger();
            list.forEach(e -> {
                log.debug("  config #{}: {} path(s)", counter.incrementAndGet(), e.root().size());
                log.trace("    {}", e);
            });
        }

        return list;
    }

    /**
     * Fetches configuration from single config supplier.
     *
     * @param source config source
     * @param query  config query
     * @return optional of fetched config, empty config if {@code supplier} is {@link ConfigSource} which allows
     *     fetching errors
     * @throws NullPointerException in case of null arguments
     * @throws RuntimeException     if supplier throws and it doesn't allow fetch errors
     * @see #fetchConfig(Supplier)
     * @see ConfigSource#allowErrors()
     */
    protected Optional<Config> fetchConfig(@NonNull ConfigSource source, @NonNull ConfigQuery query) {
        try {
            val result = fetchConfig(() -> source.get(query));
            if (log.isDebugEnabled()) {
                val numPaths = result.map(e -> e.root().size()).orElse(0);
                log.debug("{} source {} returned config with {} path(s)", this, source, numPaths);
            }
            return result;
        } catch (Exception e) {
            if (source.allowErrors()) {
                log.warn("config source {} threw exception while fetching configuration, ignoring: {}",
                    source, e.getMessage(), e);
                return Optional.empty();
            } else {
                throw Tsc4jException.of("Config source %s threw exception: %%s", e, source);
            }
        }
    }

    /**
     * Fetches configuration from single config supplier.
     *
     * @param supplier config source
     * @return optional of fetched config, empty config if {@code supplier} is {@link ConfigSource} which allows
     *     fetching errors
     * @throws NullPointerException in case of null arguments
     * @throws RuntimeException     if supplier throws and it doesn't allow fetch errors
     */
    private Optional<Config> fetchConfig(@NonNull Supplier<Config> supplier) {
        val config = supplier.get();
        if (config == null) {
            log.warn("config supplier returned null: {}", supplier);
        } else {
            log.trace("config supplier {} returned config: {}", supplier, config);
        }
        return Optional.ofNullable(config);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(size=" + sources.size() + ")";
    }

    @Override
    protected void doClose() {
        log.debug("closing {} suppliers.", sources.size());
        sources.forEach(e -> Tsc4jImplUtils.close(e, log));

        if (overrideSupplier instanceof Closeable) {
            log.debug("closing override config supplier: {}", overrideSupplier);
            Tsc4jImplUtils.close((Closeable) overrideSupplier, log);
        }

        if (fallbackSupplier instanceof Closeable) {
            log.debug("closing fallback config supplier: {}", fallbackSupplier);
            Tsc4jImplUtils.close((Closeable) fallbackSupplier, log);
        }
    }
}
