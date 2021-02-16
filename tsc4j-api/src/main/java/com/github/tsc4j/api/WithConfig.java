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

package com.github.tsc4j.api;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigMemorySize;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import lombok.NonNull;
import lombok.val;

import java.time.Duration;
import java.time.Period;
import java.time.temporal.TemporalAmount;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static com.github.tsc4j.api.ApiUtils.optionalIfPresent;

/**
 * Instances implementing this interface allow their reconfiguration with settings stored in {@link Config} instance.
 *
 * @see #withConfig(Config)
 */
@FunctionalInterface
public interface WithConfig {
    /**
     * Applies given configuration to object instance.
     *
     * @param config configuration that applies
     * @throws NullPointerException                in case of null arguments
     * @throws com.typesafe.config.ConfigException if exception is thrown by {@link Config} retrieval methods
     * @see #cfgExtract(Config, String, BiFunction)
     * @see #cfgBoolean(Config, String)
     * @see #cfgInt(Config, String)
     * @see #cfgNumber(Config, String)
     * @see #cfgLong(Config, String)
     * @see #cfgDouble(Config, String)
     * @see #cfgString(Config, String)
     * @see #cfgConfigObject(Config, String)
     * @see #cfgConfig(Config, String)
     * @see #cfgAnyRef(Config, String)
     * @see #cfgConfigValue(Config, String)
     * @see #cfgBytes(Config, String)
     * @see #cfgMemorySize(Config, String)
     * @see #cfgPeriod(Config, String)
     * @see #cfgDuration(Config, String)
     * @see #cfgTemporalAmount(Config, String)
     */
    void withConfig(@NonNull Config config);

    /**
     * Invokes extractor function if config key is present.
     *
     * @param config    config object
     * @param path      config path
     * @param extractor config value extractor function
     * @param <T>       value type
     * @return optional of extracted value.
     */
    default <T> Optional<T> cfgExtract(@NonNull Config config,
                                       @NonNull String path,
                                       @NonNull BiFunction<Config, String, T> extractor) {
        return ApiUtils.cfgExtract(config, path, extractor);
    }

    /**
     * Invokes extractor function if config key is present then invokes consumer with it.
     *
     * @param config    config object
     * @param path      config path
     * @param extractor config value extractor function
     * @param consumer  extracted value consumer
     * @param <T>       value type
     * @return optional of value.
     */
    default <T> Optional<T> cfgExtract(@NonNull Config config,
                                       @NonNull String path,
                                       @NonNull BiFunction<Config, String, T> extractor,
                                       @NonNull Consumer<T> consumer) {
        val opt = cfgExtract(config, path, extractor);
        opt.ifPresent(consumer);
        return opt;
    }

    /**
     * Extracts boolean value from given {@link Config} instance at given path.
     *
     * @param config config instance
     * @param path   config path, preferably in kebab-case format
     * @return optional of extracted value
     * @throws com.typesafe.config.ConfigException in case of config value mismatch
     */
    default Optional<Boolean> cfgBoolean(Config config, String path) {
        return cfgExtract(config, path, Config::getBoolean);
    }

    /**
     * Invokes given consumer if extraction of given path from config is successful.
     *
     * @param config   config instance
     * @param path     config path, preferably in kebab-case format
     * @param consumer consumer to invoke if extraction was successful
     */
    default Optional<Boolean> cfgBoolean(Config config, String path, Consumer<Boolean> consumer) {
        return cfgBoolean(config, path).map(it -> optionalIfPresent(it, consumer));
    }

    /**
     * Extracts {@code numeric} value from given {@link Config} instance at given path.
     *
     * @param config config instance
     * @param path   config path, preferably in kebab-case format
     * @return optional of extracted value
     * @throws com.typesafe.config.ConfigException in case of config value mismatch
     */
    default Optional<Number> cfgNumber(Config config, String path) {
        return cfgExtract(config, path, Config::getNumber);
    }

    /**
     * Invokes given consumer if extraction of given path from config is successful.
     *
     * @param config   config instance
     * @param path     config path, preferably in kebab-case format
     * @param consumer consumer to invoke if extraction was successful
     */
    default Optional<Number> cfgNumber(Config config, String path, Consumer<Number> consumer) {
        return cfgNumber(config, path).map(it -> optionalIfPresent(it, consumer));
    }

    /**
     * Extracts {@code integer} value from given {@link Config} instance at given path.
     *
     * @param config config instance
     * @param path   config path, preferably in kebab-case format
     * @return optional of extracted value
     * @throws com.typesafe.config.ConfigException in case of config value mismatch
     */
    default Optional<Integer> cfgInt(Config config, String path) {
        return cfgExtract(config, path, Config::getInt);
    }

    /**
     * Invokes given consumer if extraction of given path from config is successful.
     *
     * @param config   config instance
     * @param path     config path, preferably in kebab-case format
     * @param consumer consumer to invoke if extraction was successful
     */
    default Optional<Integer> cfgInt(Config config, String path, Consumer<Integer> consumer) {
        return cfgInt(config, path).map(it -> optionalIfPresent(it, consumer));
    }

    /**
     * Extracts {@code long} value from given {@link Config} instance at given path.
     *
     * @param config config instance
     * @param path   config path, preferably in kebab-case format
     * @return optional of extracted value
     * @throws com.typesafe.config.ConfigException in case of config value mismatch
     */
    default Optional<Long> cfgLong(Config config, String path) {
        return cfgExtract(config, path, Config::getLong);
    }

    /**
     * Invokes given consumer if extraction of given path from config is successful.
     *
     * @param config   config instance
     * @param path     config path, preferably in kebab-case format
     * @param consumer consumer to invoke if extraction was successful
     */
    default Optional<Long> cfgLong(Config config, String path, Consumer<Long> consumer) {
        return cfgLong(config, path).map(it -> optionalIfPresent(it, consumer));
    }

    /**
     * Extracts {@code double} value from given {@link Config} instance at given path.
     *
     * @param config config instance
     * @param path   config path, preferably in kebab-case format
     * @return optional of extracted value
     * @throws com.typesafe.config.ConfigException in case of config value mismatch
     */
    default Optional<Double> cfgDouble(Config config, String path) {
        return cfgExtract(config, path, Config::getDouble);
    }

    /**
     * Invokes given consumer if extraction of given path from config is successful.
     *
     * @param config   config instance
     * @param path     config path, preferably in kebab-case format
     * @param consumer consumer to invoke if extraction was successful
     */
    default Optional<Double> cfgDouble(Config config, String path, Consumer<Double> consumer) {
        return cfgDouble(config, path).map(it -> optionalIfPresent(it, consumer));
    }

    /**
     * Extracts {@code string} value from given {@link Config} instance at given path.
     *
     * @param config config instance
     * @param path   config path, preferably in kebab-case format
     * @return optional of extracted value
     * @throws com.typesafe.config.ConfigException in case of config value mismatch
     */
    default Optional<String> cfgString(Config config, String path) {
        return cfgExtract(config, path, Config::getString);
    }

    /**
     * Invokes given consumer if extraction of given path from config is successful.
     *
     * @param config   config instance
     * @param path     config path, preferably in kebab-case format
     * @param consumer consumer to invoke if extraction was successful
     */
    default Optional<String> cfgString(Config config, String path, Consumer<String> consumer) {
        return cfgString(config, path).map(it -> optionalIfPresent(it, consumer));
    }

    /**
     * Extracts {@code ConfigObject} value from given {@link Config} instance at given path.
     *
     * @param config config instance
     * @param path   config path, preferably in kebab-case format
     * @return optional of extracted value
     * @throws com.typesafe.config.ConfigException in case of config value mismatch
     */
    default Optional<ConfigObject> cfgConfigObject(Config config, String path) {
        return cfgExtract(config, path, Config::getObject);
    }

    /**
     * Invokes given consumer if extraction of given path from config is successful.
     *
     * @param config   config instance
     * @param path     config path, preferably in kebab-case format
     * @param consumer consumer to invoke if extraction was successful
     */
    default Optional<ConfigObject> cfgConfigObject(Config config, String path, Consumer<ConfigObject> consumer) {
        return cfgConfigObject(config, path).map(it -> optionalIfPresent(it, consumer));
    }

    /**
     * Extracts {@code Config} value from given {@link Config} instance at given path.
     *
     * @param config config instance
     * @param path   config path, preferably in kebab-case format
     * @return optional of extracted value
     * @throws com.typesafe.config.ConfigException in case of config value mismatch
     */
    default Optional<Config> cfgConfig(Config config, String path) {
        return cfgExtract(config, path, Config::getConfig);
    }

    /**
     * Invokes given consumer if extraction of given path from config is successful.
     *
     * @param config   config instance
     * @param path     config path, preferably in kebab-case format
     * @param consumer consumer to invoke if extraction was successful
     */
    default Optional<Config> cfgConfig(Config config, String path, Consumer<Config> consumer) {
        return cfgConfig(config, path).map(it -> optionalIfPresent(it, consumer));
    }

    /**
     * Extracts {@code any-obj-value} value from given {@link Config} instance at given path.
     *
     * @param config config instance
     * @param path   config path, preferably in kebab-case format
     * @return optional of extracted value
     * @throws com.typesafe.config.ConfigException in case of config value mismatch
     */
    default Optional<Object> cfgAnyRef(Config config, String path) {
        return cfgExtract(config, path, Config::getAnyRef);
    }

    /**
     * Invokes given consumer if extraction of given path from config is successful.
     *
     * @param config   config instance
     * @param path     config path, preferably in kebab-case format
     * @param consumer consumer to invoke if extraction was successful
     */
    default Optional<Object> cfgAnyRef(Config config, String path, Consumer<Object> consumer) {
        return cfgAnyRef(config, path).map(it -> optionalIfPresent(it, consumer));
    }

    /**
     * Extracts {@code ConfigValue} value from given {@link Config} instance at given path.
     *
     * @param config config instance
     * @param path   config path, preferably in kebab-case format
     * @return optional of extracted value
     * @throws com.typesafe.config.ConfigException in case of config value mismatch
     */
    default Optional<ConfigValue> cfgConfigValue(Config config, String path) {
        return cfgExtract(config, path, Config::getValue);
    }

    /**
     * Invokes given consumer if extraction of given path from config is successful.
     *
     * @param config   config instance
     * @param path     config path, preferably in kebab-case format
     * @param consumer consumer to invoke if extraction was successful
     */
    default Optional<ConfigValue> cfgConfigValue(Config config, String path, Consumer<ConfigValue> consumer) {
        return cfgConfigValue(config, path).map(it -> optionalIfPresent(it, consumer));
    }

    /**
     * Extracts {@code bytes} value from given {@link Config} instance at given path.
     *
     * @param config config instance
     * @param path   config path, preferably in kebab-case format
     * @return optional of extracted value
     * @throws com.typesafe.config.ConfigException in case of config value mismatch
     */
    default Optional<Long> cfgBytes(Config config, String path) {
        return cfgExtract(config, path, Config::getBytes);
    }

    /**
     * Invokes given consumer if extraction of given path from config is successful.
     *
     * @param config   config instance
     * @param path     config path, preferably in kebab-case format
     * @param consumer consumer to invoke if extraction was successful
     */
    default Optional<Long> cfgBytes(Config config, String path, Consumer<Long> consumer) {
        return cfgBytes(config, path).map(it -> optionalIfPresent(it, consumer));
    }

    /**
     * Extracts {@code bytes} value from given {@link Config} instance at given path.
     *
     * @param config config instance
     * @param path   config path, preferably in kebab-case format
     * @return optional of extracted value
     * @throws com.typesafe.config.ConfigException in case of config value mismatch
     */
    default Optional<ConfigMemorySize> cfgMemorySize(Config config, String path) {
        return cfgExtract(config, path, Config::getMemorySize);
    }

    /**
     * Invokes given consumer if extraction of given path from config is successful.
     *
     * @param config   config instance
     * @param path     config path, preferably in kebab-case format
     * @param consumer consumer to invoke if extraction was successful
     */
    default Optional<ConfigMemorySize> cfgMemorySize(Config config, String path, Consumer<ConfigMemorySize> consumer) {
        return cfgMemorySize(config, path).map(it -> optionalIfPresent(it, consumer));
    }

    /**
     * Extracts {@code Duration} value from given {@link Config} instance at given path.
     *
     * @param config config instance
     * @param path   config path, preferably in kebab-case format
     * @return optional of extracted value
     * @throws com.typesafe.config.ConfigException in case of config value mismatch
     */
    default Optional<Duration> cfgDuration(Config config, String path) {
        return cfgExtract(config, path, Config::getDuration);
    }

    /**
     * Invokes given consumer if extraction of given path from config is successful.
     *
     * @param config   config instance
     * @param path     config path, preferably in kebab-case format
     * @param consumer consumer to invoke if extraction was successful
     */
    default Optional<Duration> cfgDuration(Config config, String path, Consumer<Duration> consumer) {
        return cfgDuration(config, path).map(it -> optionalIfPresent(it, consumer));
    }

    /**
     * Extracts {@code Period} value from given {@link Config} instance at given path.
     *
     * @param config config instance
     * @param path   config path, preferably in kebab-case format
     * @return optional of extracted value
     * @throws com.typesafe.config.ConfigException in case of config value mismatch
     */
    default Optional<Period> cfgPeriod(Config config, String path) {
        return cfgExtract(config, path, Config::getPeriod);
    }

    /**
     * Invokes given consumer if extraction of given path from config is successful.
     *
     * @param config   config instance
     * @param path     config path, preferably in kebab-case format
     * @param consumer consumer to invoke if extraction was successful
     */
    default Optional<Period> cfgPeriod(Config config, String path, Consumer<Period> consumer) {
        return cfgPeriod(config, path).map(it -> optionalIfPresent(it, consumer));
    }

    /**
     * Extracts {@code TemporalAmout} value from given {@link Config} instance at given path.
     *
     * @param config config instance
     * @param path   config path, preferably in kebab-case format
     * @return optional of extracted value
     * @throws com.typesafe.config.ConfigException in case of config value mismatch
     */
    default Optional<TemporalAmount> cfgTemporalAmount(Config config, String path) {
        return cfgExtract(config, path, Config::getTemporal);
    }

    /**
     * Invokes given consumer if extraction of given path from config is successful.
     *
     * @param config   config instance
     * @param path     config path, preferably in kebab-case format
     * @param consumer consumer to invoke if extraction was successful
     */
    default Optional<TemporalAmount> cfgTemporalAmount(Config config, String path, Consumer<TemporalAmount> consumer) {
        return cfgTemporalAmount(config, path).map(it -> optionalIfPresent(it, consumer));
    }
}
