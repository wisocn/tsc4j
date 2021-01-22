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

import com.typesafe.config.Config;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * {@link ConfigTransformer} implementation that delegates transformation to multiple wrapped config transformers.
 */
@Slf4j
public final class AggConfigTransformer implements ConfigTransformer {
    private static final String TYPE = "agg";

    /**
     * {@link ConfigTransformer} delegates.
     */
    @Getter(AccessLevel.PROTECTED)
    private final List<ConfigTransformer> transformers;
    private final String toString;

    /**
     * Creates new instance.
     *
     * @param transformers array of transformers
     */
    public AggConfigTransformer(@NonNull ConfigTransformer... transformers) {
        this(Arrays.asList(transformers));
    }

    /**
     * Creates new instance.
     *
     * @param transformers collection of transformers
     */
    public AggConfigTransformer(@NonNull Collection<ConfigTransformer> transformers) {
        val list = transformers.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        this.transformers = Collections.unmodifiableList(list);
        this.toString = getClass().getSimpleName() + "@" + hashCode();
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getName() {
        return TYPE;
    }

    @Override
    public boolean allowErrors() {
        return false;
    }

    @Override
    public Config transform(@NonNull Config config) {
        if (transformers.isEmpty()) {
            log.debug("{} no transformers assigned, returning provided config: {}", this, config);
            return config;
        }
        log.trace("{} transforming config: {}", this, config);
        return transformers.stream()
            .reduce(config, this::transformConfig, (cfgA, cfgB) -> cfgB);
    }

    @Override
    public void close() {
        transformers.forEach(e -> Tsc4jImplUtils.close(e, log));
    }

    /**
     * Transforms configuration using single transformer.
     *
     * @param config      config to transform
     * @param transformer transformer to use
     * @return transformed config
     * @throws NullPointerException in case of null arguments
     * @throws RuntimeException     when exception occurs during transformation and transformed doesn't allow errors
     * @see ConfigTransformer#allowErrors()
     */
    protected Config transformConfig(@NonNull Config config, @NonNull ConfigTransformer transformer) {
        try {
            log.trace("{} transforming config with transformer {}: {}", this, transformer, config);
            val transformedConfig = transformer.transform(config);
            if (transformedConfig == null) {
                log.warn("{} config transformer {} returned null, returning original config.",
                    this, transformer);
                return config;
            }
            log.trace("{} config transformer {} result:\n  ORIG: {}\n  NEW:  {}",
                this, transformer, config, transformedConfig);
            return transformedConfig;
        } catch (Exception e) {
            if (transformer.allowErrors()) {
                log.warn("{} error while transforming config with transformer {}: {} " +
                        "(error tolerance is enabled, returning original config)",
                    this, transformer, e.getMessage(), e);
                return config;
            }
            throw Tsc4jException.of("Config transformer %s threw exception: %%s", e, transformer);
        }
    }

    @Override
    public String toString() {
        return toString;
    }
}
