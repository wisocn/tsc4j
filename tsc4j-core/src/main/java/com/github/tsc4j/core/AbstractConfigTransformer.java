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
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigUtil;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.val;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static com.typesafe.config.ConfigValueType.*;

/**
 * Base class for writing {@link ConfigTransformer} aliases.
 *
 * @param <T> transformation context type
 */
public abstract class AbstractConfigTransformer<T> extends BaseInstance implements ConfigTransformer {
    /**
     * Tells whether transformer errors (exceptions should be tolerated)
     */
    private final boolean allowErrors;

    /**
     * Tells whether operations should be done in parallel if possible.
     */
    @Getter(AccessLevel.PROTECTED)
    private final boolean parallel;

    protected AbstractConfigTransformer(@NonNull ConfigTransformerBuilder<?> builder) {
        super(builder.getName());

        builder.checkState();

        this.allowErrors = builder.isAllowErrors();
        this.parallel = builder.isParallel();
    }

    @Override
    public final boolean allowErrors() {
        return allowErrors;
    }

    /**
     * Creates transformation context from given {@link Config} instance.<p/>
     * You may probably want to scan configuration for paths/values you're interested in
     * using {@link Tsc4jImplUtils#scanConfig(Config, BiConsumer)}
     *
     * @param config config for which to create context for.
     * @return transformation context
     * @see Tsc4jImplUtils#scanConfig(Config, BiConsumer)
     * @see Tsc4jImplUtils#scanConfigObject(ConfigObject, BiConsumer)
     * @see #transformBoolean(String, ConfigValue, Object)
     * @see #transformNumber(String, ConfigValue, Object)
     * @see #transformString(String, ConfigValue, Object)
     * @see #transformNull(String, ConfigValue, Object)
     * @see #transformList(String, ConfigList, Object)
     * @see #transformObject(String, ConfigObject, Object)
     * @see #transformConfigValue(String, ConfigValue, Object)
     */
    protected abstract T createTransformationContext(Config config);

    @Override
    public Config transform(@NonNull Config config) {
        val ctx = createTransformationContext(config);
        log.debug("{} created transformation context: {}", this, ctx);
        return config.entrySet().stream()
            .sorted(Comparator.comparing(Map.Entry::getKey))
            .map(e -> toTransformedConfig(e, ctx))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .reduce(ConfigFactory.empty(), (acc, current) -> current.withFallback(acc));
    }

    private Optional<Config> toTransformedConfig(@NonNull Map.Entry<String, ConfigValue> e, T ctx) {
        val path = fixConfigPath(e.getKey());

        return Optional.ofNullable(transformConfigValue(path, e.getValue(), ctx))
            .map(x -> x.atPath(e.getKey()));
    }

    /**
     * Tries to fix config path if required. Config paths sometimes contain quotes and even escaped quotes, this method
     * removes them.
     *
     * @param path config path
     * @return fixed config path.
     */
    protected String fixConfigPath(@NonNull String path) {
        val result = path.replace("\\\"", "").replace("\"", "");
        if (log.isDebugEnabled() && !result.equals(path)) {
            log.debug("fixConfigPath(): '{}' -> '{}'", path, result);
        }
        return result;
    }

    private ConfigValue transformConfigValue(@NonNull String path, @NonNull ConfigValue value, T ctx) {
        // remove quotes from config path, consequence of using ConfigUtil.joinPath()
        path = path.replace("\"", "");

        val type = value.valueType();
        ConfigValue result = null;
        if (type == OBJECT) {
            result = transformObject(path, (ConfigObject) value, ctx);
        } else if (type == NUMBER) {
            result = transformNumber(path, value, ctx);
        } else if (type == STRING) {
            result = transformString(path, value, ctx);
        } else if (type == BOOLEAN) {
            result = transformBoolean(path, value, ctx);
        } else if (type == LIST) {
            result = transformList(path, (ConfigList) value, ctx);
        } else if (type == NULL) {
            result = transformNull(path, value, ctx);
        }

        if (!value.equals(result)) {
            log.trace("{} transformed config path {} from {} to {}", this, path, value, result);
        } else {
            log.trace("{} config path unchanged after transformation: {}", this, path);
        }

        return result;
    }

    /**
     * Transforms {@link ConfigObject} config value.
     *
     * @param path   config path
     * @param object config object
     * @param ctx    transformation context, may be null
     * @return transformed config value
     * @see #createTransformationContext(Config)
     */
    protected ConfigValue transformObject(@NonNull String path, @NonNull ConfigObject object, T ctx) {
        val empty = ConfigFactory.empty();
        val result = object.entrySet().stream()
            .sorted(Comparator.comparing(Map.Entry::getKey))
            .map(e -> empty.withValue(e.getKey(), transformConfigValue(ConfigUtil.joinPath(path, e.getKey()), e.getValue(), ctx)))
            .reduce(empty, (acc, current) -> current.withFallback(acc))
            .root();

        log.trace("{} transformed config object path {} from {} to {}", this, path, object, result);
        return result;
    }

    /**
     * Transforms {@link ConfigList} value.
     *
     * @param path config path
     * @param list config list
     * @param ctx  transformation context, may be null
     * @return transformed config value
     * @see #createTransformationContext(Config)
     */
    protected ConfigValue transformList(@NonNull String path, @NonNull ConfigList list, T ctx) {
        val result = list.stream()
            .map(e -> transformConfigValue(path, e, ctx))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        log.trace("{} transformed config list at {} from {} to {}", this, path, list, result);
        return ConfigValueFactory.fromIterable(result);
    }

    /**
     * Transforms {@link com.typesafe.config.ConfigValueType#STRING} config value.
     *
     * @param path  config path
     * @param value config value
     * @param ctx   transformation context, may be null
     * @return transformed config value
     * @see #createTransformationContext(Config)
     */
    protected ConfigValue transformString(String path, ConfigValue value, T ctx) {
        return value;
    }

    /**
     * Transforms {@link com.typesafe.config.ConfigValueType#NUMBER} config value.
     *
     * @param path  config path
     * @param value config value
     * @param ctx   transformation context, may be null
     * @return transformed config value
     * @see #createTransformationContext(Config)
     */
    protected ConfigValue transformNumber(String path, ConfigValue value, T ctx) {
        return value;
    }

    /**
     * Transforms {@link com.typesafe.config.ConfigValueType#BOOLEAN} config value.
     *
     * @param path  config path
     * @param value config value
     * @param ctx   transformation context, may be null
     * @return transformed config value
     * @see #createTransformationContext(Config)
     */
    protected ConfigValue transformBoolean(String path, ConfigValue value, T ctx) {
        return value;
    }

    /**
     * Transforms {@link com.typesafe.config.ConfigValueType#NULL} config value.
     *
     * @param path  config path
     * @param value config value
     * @param ctx   transformation context, may be null
     * @return transformed config value
     * @see #createTransformationContext(Config)
     */
    protected ConfigValue transformNull(String path, ConfigValue value, T ctx) {
        return value;
    }
}
