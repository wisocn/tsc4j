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

import com.github.tsc4j.api.ReloadableConfig;
import com.github.tsc4j.api.Tsc4jBeanBuilder;
import com.github.tsc4j.api.WithConfig;
import com.typesafe.config.Config;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

import java.time.Duration;
import java.util.List;

/**
 * Tsc4j bootstrap configuration. Contains all required settings for initializing configuration sources,
 * transformers, etc.
 *
 * @see Tsc4j
 * @see ReloadableConfigFactory
 */
@Value
@Builder(toBuilder = true)
@Tsc4jBeanBuilder
public class Tsc4jConfig {
    /**
     * Configuration refresh interval.
     */
    @Default
    Duration refreshInterval = Duration.ofMinutes(2);

    /**
     * Refresh interval jitter in percentage (0 - 100); it's meant to add some randomness to {@link
     * #getRefreshInterval()}.
     *
     * @see #getRefreshInterval()
     */
    @Default
    int refreshIntervalJitterPct = 25;

    /**
     * whether reloadables are notified in reverse order (value: "reverseUpdateOrder")
     */
    @Default
    boolean reverseUpdateOrder = false;

    /**
     * Log first configuration fetch?
     */
    @Default
    boolean logFirstFetch = true;

    /**
     * Tells whether creation of {@link ReloadableConfig} will automatically append {@link
     * com.github.tsc4j.core.impl.CliConfigSource} to list of initialized sources. (default: true)
     */
    @Default
    boolean cliEnabled = true;

    /**
     * List of configuration source configurations.
     *
     * @see ConfigSource
     */
    @Singular("source")
    List<Config> sources;

    /**
     * List of configuration transformer configurations.
     *
     * @see ConfigTransformer
     */
    @Singular("transformer")
    List<Config> transformers;

    /**
     * List of value provider configurations.
     *
     * @see ConfigValueProvider
     */
    @Singular("valueProvider")
    List<Config> valueProviders;

    /**
     * Builder for {@link Tsc4jConfig}.
     */
    public static final class Tsc4jConfigBuilder implements WithConfig {
        @Override
        public void withConfig(@NonNull Config config) {
            cfgDuration(config, "refresh-interval", this::refreshInterval);
            cfgInt(config, "refresh-interval-jitter-pct", this::refreshIntervalJitterPct);
            cfgBoolean(config, "reverse-update-order", this::reverseUpdateOrder);
            cfgBoolean(config, "cli-enabled", this::cliEnabled);
            cfgExtract(config, "sources", Config::getConfigList, this::sources);
            cfgExtract(config, "transformers", Config::getConfigList, this::transformers);
            cfgExtract(config, "value-providers", Config::getConfigList, this::valueProviders);
        }
    }
}
