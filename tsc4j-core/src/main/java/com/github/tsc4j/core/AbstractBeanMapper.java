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

import com.github.tsc4j.api.ConfigValueDecoder;
import com.github.tsc4j.core.impl.Deserializers;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.val;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Base class for writing {@link BeanMapper} implementations.
 */
public abstract class AbstractBeanMapper implements BeanMapper {
    /**
     * tsc4j property name for disabling custom config value converters discovery (value: <b>{@value}</b>)
     *
     * @see #customValueConverters()
     */
    protected static final String PROP_NAME_CUSTOM_CONVERTERS_ENABLED = "custom-value-converters.enabled";

    protected final Logger log = LoggerFactory.getLogger(getClass());
    /**
     * Value converters.
     */
    @Getter(AccessLevel.PROTECTED)
    private final ByClassRegistry<Function<ConfigValue, ?>> valueConverters = defaultValueConverters();
    private final ByClassRegistry<BiFunction<Config, String, ?>> configConverters =
        Deserializers.convertersLightbendConfig();

    @Override
    @SuppressWarnings("unchecked")
    public <T> T create(@NonNull Class<T> clazz, @NonNull Config config, @NonNull String path) {
        if (!config.isResolved()) {
            throw new ConfigException.NotResolved(
                "Config instance is not resolved. See the API docs for Config#resolve()");
        }

        val realPath = Tsc4j.configPath(path);

        if (log.isTraceEnabled()) {
            log.trace("create({}, {}, {}): entry-point.", clazz.getName(), config.hashCode(), realPath);
        }

        if (!(realPath.isEmpty() || config.hasPathOrNull(realPath))) {
            throw new ConfigException.BadPath(config.origin(), path,
                "Config instance doesn't contain value at path: " + realPath);
        }

        // asking for enum?
        if (clazz.isEnum()) {
            return (T) toEnum((Class<Enum>) clazz, config.getValue(realPath), realPath);
        }

        // ask for config, path converter function and execute it if found.
        return getConfigConverter(clazz)
            .map(function -> (T) runConverterFunction(function, config, realPath))
            // if that fails, just try to convert config value to something
            .orElseGet(() -> {
                val value = realPath.isEmpty() ? config.root() : config.getValue(realPath);
                return create(clazz, value, realPath);
            });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T create(@NonNull Class<T> clazz, @NonNull ConfigValue value, @NonNull String path) {
        if (value.valueType() == ConfigValueType.NULL) {
            return null;
        }

        return getConfigValueConverter(clazz)
            .map(function -> (T) runConverterFunction(function, clazz, value, path))
            .orElseGet(() -> (T) createBean(clazz, value, path));
    }

    /**
     * Creates bean from given config value if there is no converter available.
     *
     * @param clazz bean class to instantiate.
     * @param value config value
     * @param path  {@link Config} path at which given {@code value} was retrieved.
     * @param <T>   bean class type
     * @return initialized bean class
     */
    protected abstract <T> T createBean(Class<T> clazz, ConfigValue value, String path);

    /**
     * Runs given {@link ConfigValue} converter function.
     *
     * @param function function to invoke
     * @param value    config value to convert
     * @param path     {@link Config} path at which this value was retrieved
     * @return converted config value
     * @throws com.typesafe.config.ConfigException.BadValue if converter function fails.
     * @see #getConfigValueConverter(Class)
     * @see #getValueConverters()
     */
    protected Object runConverterFunction(@NonNull Function<ConfigValue, ?> function,
                                          @NonNull Class<?> clazz,
                                          @NonNull ConfigValue value,
                                          @NonNull String path) {
        return withConfigValue(
            () -> function.apply(value),
            value.origin(), path, "Cannot convert config value to " + clazz.getName());
    }

    /**
     * Runs given {@link ConfigValue} converter function.
     *
     * @param function function to invoke
     * @param config   config instance
     * @param path     {@link Config} path at which this value was retrieved
     * @return converted config value
     * @throws com.typesafe.config.ConfigException.BadValue if converter function fails.
     * @see #getConfigValueConverter(Class)
     * @see #getValueConverters()
     */
    protected Object runConverterFunction(@NonNull BiFunction<Config, String, ?> function,
                                          @NonNull Config config,
                                          @NonNull String path) {
        return withConfigValue(
            () -> function.apply(config, path),
            config.origin(), path,
            "Can't convert config instance.");
    }

    /**
     * Deserializes enum from config value.
     *
     * @param <T>   enum type
     * @param clazz enum class
     * @param value config value
     * @param path  config path of the config value
     * @return enum
     * @throws RuntimeException if enum can't be deserialized
     */
    protected <T extends Enum<T>> T toEnum(Class<T> clazz, ConfigValue value, String path) {
        val s = value.unwrapped().toString().trim();
        return withConfigValue(() -> Enum.valueOf(clazz, s), value.origin(), path, "Invalid enum constant.");
    }

    /**
     * Runs supplier that might throw exception.
     *
     * @param supplier         supplier
     * @param origin           config value origin
     * @param path             config path
     * @param exceptionMessage {@link ConfigException.BadValue} exception message if supplier throws exception.
     * @param <T>              result type
     * @return supplier provided result
     * @throws ConfigException when supplier throws
     */
    protected final <T> T withConfigValue(@NonNull Supplier<T> supplier,
                                          @NonNull ConfigOrigin origin,
                                          @NonNull String path,
                                          @NonNull String exceptionMessage) {
        try {
            return supplier.get();
        } catch (ConfigException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigException.BadValue(origin, path, exceptionMessage, e);
        }
    }

    /**
     * Returns {@link BiFunction} that converts values of {@link Config} instance at given path to bean instance.
     *
     * @param clazz bean class
     * @param <T>   bean class type
     * @return optional of converter function.
     */
    @SuppressWarnings("unchecked")
    protected final <T> Optional<BiFunction<Config, String, T>> getConfigConverter(@NonNull Class<T> clazz) {
        return configConverters.get(clazz)
            .map(e -> (BiFunction<Config, String, T>) e);
    }

    /**
     * Returns {@link Function} that converts given stringified config value to bean instance of given type.
     *
     * @param clazz bean class
     * @param <T>   bean class type
     * @return optional of converter function
     */
    @SuppressWarnings("unchecked")
    protected final <T> Optional<Function<ConfigValue, T>> getConfigValueConverter(@NonNull Class<T> clazz) {
        return valueConverters.get(clazz)
            .map(e -> (Function<ConfigValue, T>) e);
    }

    /**
     * Concatenates config path.
     *
     * @param base   base path
     * @param chunks additional path chunks (should not begin/end with {@code .} character)
     * @return concatenated config path
     */
    protected String catPath(String base, String... chunks) {
        val s1 = base.isEmpty() ? Stream.<String>empty() : Stream.of(base);
        val s2 = Tsc4jImplUtils.uniqStream(Arrays.asList(chunks));
        return Stream.concat(s1, s2)
            .collect(Collectors.joining("."));
    }

    /**
     * Creates default config value -&lt; to T converters
     *
     * @return immutable registry of per-class converter functions
     */
    protected ByClassRegistry<Function<ConfigValue, ?>> defaultValueConverters() {
        return Deserializers.convertersLightbendConfigValue()
            .add(Deserializers.convertersJavaPrimitives())
            .add(Deserializers.convertersJava())
            .add(Deserializers.convertersJavaTime())
            .add(Deserializers.convertersJdbc())
            .add(Deserializers.convertersJavaCrypto())
            .add(customValueConverters())
            ;
    }

    /**
     * Creates {@link ByClassRegistry} by discovering implementations of {@link ConfigValueDecoder} on classpath.
     * Custom
     * config value converters discovery can be disabled by setting {@value #PROP_NAME_CUSTOM_CONVERTERS_ENABLED} to
     * {@code false}.
     *
     * @return registry containing discovered custom value converters if discovery is enabled, otherwise empty registry
     */
    protected final ByClassRegistry<Function<ConfigValue, ?>> customValueConverters() {
        val empty = ByClassRegistry.<Function<ConfigValue, ?>>empty();

        // check whether custom value converters discovery is enabled
        if (!isCustomValueConverterDiscoveryEnabled()) {
            log.debug("custom config value decoder discovery is disabled.");
            return empty;
        }

        val decoders = Tsc4jImplUtils.loadImplementations(ConfigValueDecoder.class)
            .stream()
            .sorted(Comparator.comparing(ConfigValueDecoder::getOrder))
            .collect(Collectors.toList());

        if (decoders.isEmpty()) {
            log.debug("didn't discover any custom config value decoders.");
        } else {
            log.info("discovered {} custom config value decoder(s).", decoders.size());
            if (log.isDebugEnabled()) {
                decoders.forEach(e -> log.debug("  {} -> {}", e.forClass().getName(), e));
            }
        }

        // convert decoders to by class -> function registry
        return decoders.stream()
            .reduce(empty, (acc, decoder) -> acc.add(decoder.forClass(), decoder::decode), (a, b) -> b);
    }

    /**
     * Tells whether custom config value converter discovery is enabled.
     *
     * @return true/false
     * @see #PROP_NAME_CUSTOM_CONVERTERS_ENABLED
     * @see #customValueConverters()
     */
    private boolean isCustomValueConverterDiscoveryEnabled() {
        return Tsc4jImplUtils.tsc4jPropValue(PROP_NAME_CUSTOM_CONVERTERS_ENABLED)
            .map(Boolean::parseBoolean)
            .orElse(true);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "@" + hashCode();
    }
}
