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

package com.github.tsc4j.core.creation;

import com.github.tsc4j.core.Tsc4jException;
import com.github.tsc4j.core.Tsc4jImplUtils;
import com.github.tsc4j.core.impl.SvcLoader;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.Closeable;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link InstanceCreator}-based creation utilities.
 */
@Slf4j
@UtilityClass
public class InstanceCreators {
    private static final List<String> IMPLS = Tsc4jImplUtils.unmodifiableList("impl", "implementation");

    private static final String PATH_ENABLED = "enabled";

    /**
     * Creates instance creator of given type and implementation name;
     *
     * @param creatorType  instance creator type; <b>NOTE:</b> must be discoverable with {@link java.util.ServiceLoader}
     *                     mechanism
     * @param implTypeName implementation type name
     * @param <B>          type that instance creator is able to create
     * @param <T>          instance creator type
     * @return optional of instance creator
     * @throws IllegalArgumentException if {@code creatorType} is not an interface or if implementation name is empty
     */
    public <B, T extends InstanceCreator<B>> Optional<T> instanceCreator(@NonNull Class<T> creatorType,
                                                                         @NonNull String implTypeName) {
        if (!creatorType.isInterface()) {
            throw new IllegalArgumentException("Type is not an interface: " + creatorType.getName());
        }
        if (!InstanceCreator.class.isAssignableFrom(creatorType)) {
            throw new IllegalArgumentException("Creator type should extend " + creatorType.getName());
        }

        val impl = implTypeName.trim();
        if (impl.isEmpty()) {
            throw new IllegalArgumentException("Implementation name should not be an empty string.");
        }

        return SvcLoader.unorderedStream(creatorType)
            .filter(it -> it.supports(impl))
            .sorted()
            .findFirst();
    }

    /**
     * Creates instance creator.
     *
     * @param creatorType  instance creator type; <b>NOTE:</b> must be discoverable with {@link java.util.ServiceLoader}
     *                     mechanism
     * @param implTypeName implementation type name
     * @param <B>          type that instance creator is able to create
     * @param <T>          instance creator type
     * @return instance creator
     * @throws IllegalArgumentException if instance creator with given implementation can't be be loaded
     */
    public <B, T extends InstanceCreator<B>> T loadInstanceCreator(@NonNull Class<T> creatorType,
                                                                   @NonNull String implTypeName) {
        return instanceCreator(creatorType, implTypeName)
            .orElseThrow(() -> new IllegalArgumentException("No creators found on classpath for interface " +
                creatorType.getName() + " that would be able to create implementation type name: " + implTypeName));
    }

    /**
     * Creates instance using instance creator with given type configured with given configuration.
     *
     * @param <B>         type that instance creator is able to create
     * @param <T>         instance creator type
     * @param creatorType instance creator type; <b>NOTE:</b> must be discoverable with {@link java.util.ServiceLoader}
     *                    mechanism
     * @param config      configuration that contains implementation name and instance creator's configuration
     * @return optional of created instance if it's not disabled
     * @throws RuntimeException if instance can't be created
     * @see #createNamed(Class, Config, BiFunction)
     */
    public <B, T extends InstanceCreator<B>> Optional<B> create(@NonNull Class<T> creatorType,
                                                                @NonNull Config config) {
        return create(creatorType, config, Function.identity());
    }

    /**
     * Creates instance using instance creator with given type configured with given configuration.
     *
     * @param creatorType       instance creator type; <b>NOTE:</b> must be discoverable with {@link
     *                          java.util.ServiceLoader} mechanism
     * @param config            configuration that contains implementation name and instance creator's configuration
     * @param creatorCustomizer instance creator customizer
     * @param <B>               type that instance creator is able to create
     * @param <T>               instance creator type
     * @return optional of created instance if it's not disabled
     * @throws RuntimeException if instance can't be created
     * @see #createNamed(Class, Config, BiFunction)
     */
    public <B, T extends InstanceCreator<B>> Optional<B> create(@NonNull Class<T> creatorType,
                                                                @NonNull Config config,
                                                                @NonNull Function<T, T> creatorCustomizer) {
        if (!isEnabledConfig(config)) {
            return Optional.empty();
        }

        val instance = createNamedInstance(
            creatorType, config, (name, creator) -> creatorCustomizer.apply(creator), "0");
        return Optional.ofNullable(instance);
    }

    /**
     * Creates multiple instances using instance creators.
     *
     * @param creatorType instance creator type; <b>NOTE:</b> must be discoverable with {@link java.util.ServiceLoader}
     *                    mechanism
     * @param configs     List of instance configurations contain implementation name and instance creator's
     *                    configuration
     * @param <B>         type that instance creator is able to create
     * @param <T>         instance creator type
     * @return optional of created instance if it's not disabled
     * @throws IllegalArgumentException if any enabled instance can't be created
     * @see #createNamed(Class, Config, BiFunction)
     */
    public <B, T extends InstanceCreator<B>> List<B> create(@NonNull Class<T> creatorType,
                                                            @NonNull Collection<Config> configs) {
        return create(creatorType, configs, Function.identity());
    }

    /**
     * Creates multiple instances using instance creators.
     *
     * @param creatorType instance creator type; <b>NOTE:</b> must be discoverable with {@link java.util.ServiceLoader}
     *                    mechanism
     * @param configList  {@link ConfigList} containing instance instance configurations - only {@link
     *                    com.typesafe.config.ConfigObject} instances will be taken into consideration.
     * @param <B>         type that instance creator is able to create
     * @param <T>         instance creator type
     * @return optional of created instance if it's not disabled
     * @throws IllegalArgumentException if any enabled instance can't be created
     * @see #create(Class, Collection)
     * @see #createNamed(Class, Config, BiFunction)
     */
    public <B, T extends InstanceCreator<B>> List<B> create(@NonNull Class<T> creatorType,
                                                            @NonNull ConfigList configList) {
        return create(creatorType, configList, Function.identity());
    }

    /**
     * Creates multiple instances using instance creators.
     *
     * @param creatorType       instance creator type; <b>NOTE:</b> must be discoverable with {@link
     *                          java.util.ServiceLoader} mechanism
     * @param configList        {@link ConfigList} containing instance instance configurations - only {@link
     *                          com.typesafe.config.ConfigObject} instances will be taken into consideration.
     * @param creatorCustomizer configured instance creator customizer
     * @param <B>               type that instance creator is able to create
     * @param <T>               instance creator type
     * @return list of created instances
     * @throws IllegalArgumentException if any enabled instance can't be created
     * @see #create(Class, Collection, Function)
     */
    public <B, T extends InstanceCreator<B>> List<B> create(@NonNull Class<T> creatorType,
                                                            @NonNull ConfigList configList,
                                                            @NonNull Function<T, T> creatorCustomizer) {
        val configs = configList.stream()
            .map(it -> toConfig(it))
            .collect(Collectors.toList());
        return create(creatorType, configs, creatorCustomizer);
    }

    private Config toConfig(ConfigValue cfgVal) {
        if (cfgVal == null || cfgVal.valueType() != ConfigValueType.OBJECT) {
            return ConfigFactory.empty();
        } else {
            return ((ConfigObject) cfgVal).toConfig();
        }
    }

    /**
     * Creates multiple instances using instance creators.
     *
     * @param creatorType       instance creator type; <b>NOTE:</b> must be discoverable with {@link
     *                          java.util.ServiceLoader} mechanism
     * @param configs           List of instance configurations contain implementation name and instance creator's
     *                          configuration
     * @param creatorCustomizer configured instance creator customizer
     * @param <B>               type that instance creator is able to create
     * @param <T>               instance creator type
     * @return list of created instances
     * @throws RuntimeException if any enabled instance can't be created; in this case all previously created instances,
     *                          if there are any, of course will get closed if they implement {@link java.io.Closeable}
     *                          or {@link AutoCloseable}
     * @see #create(Class, ConfigList, Function)
     * @see #createNamed(Class, Config, BiFunction)
     */
    public <B, T extends InstanceCreator<B>> List<B> create(@NonNull Class<T> creatorType,
                                                            @NonNull Collection<Config> configs,
                                                            @NonNull Function<T, T> creatorCustomizer) {
        val counter = new AtomicInteger();
        val namedConfig = configs.stream()
            .peek(it -> counter.incrementAndGet())
            .filter(Objects::nonNull)
            .map(it -> it.atPath("cfg-num-" + counter.get()))
            .reduce(ConfigFactory.empty(), (cur, prev) -> cur.withFallback(prev));

        log.debug("created named config: {}", namedConfig);
        val map = createNamed(creatorType, namedConfig, (name, creator) -> creatorCustomizer.apply(creator));
        return new ArrayList<>(map.values());
    }

    /**
     * Creates map of named instances from a given configuration.</p>
     *
     * @param creatorType instance creator type; <b>NOTE:</b> must be discoverable with {@link java.util.ServiceLoader}
     *                    mechanism
     * @param config      config instance containing at least one {@link String} => {@link Config} mapping
     * @param <B>         created instance type
     * @param <T>         creator type
     * @return map of {@link String} => created instance mappings
     * @throws RuntimeException if any enabled instance can't be created; in this case all previously created instances,
     *                          if there are any, of course will get closed if they implement {@link java.io.Closeable}
     *                          or {@link AutoCloseable}
     */
    public <B, T extends InstanceCreator<B>> Map<String, B> createNamed(@NonNull Class<T> creatorType,
                                                                        @NonNull Config config) {
        return createNamed(creatorType, config, (name, creator) -> creator);
    }

    /**
     * Creates map of named instances from a given configuration.</p>
     *
     * Example config:
     * <pre>{@code
     *
     * # named configuration: foo
     * foo: {
     *  # implementation name or fully qualified class name
     *  impl: "some-super-impl"
     *
     *  # each instance is enabled by default
     *  # enabled: false
     *
     *  # per-implementation custom config
     *  some-int:       10,
     *  some-bool:      true
     * }
     *
     * # named configuration: bar (disabled)
     * bar: {
     *  impl:       no-op
     *  enabled:    false
     *
     *  # per-implementation custom config
     *  x:          42,
     *  y:          false
     * }
     *
     * # empty config (will be skipped)
     * empty: {
     * }
     *
     * # invalid config, doesn't contain {@code implementation} path, will be skipped
     * invalid {
     *   foo: bar
     * }
     *
     * # named configuration: baz
     * baz: {
     *   impl:      some-other-impl
     *   enabled:   true
     *   x:         21
     *   y:         false
     * }
     * }
     * </pre>
     *
     * @param creatorType       instance creator type; <b>NOTE:</b> must be discoverable with {@link
     *                          java.util.ServiceLoader} mechanism
     * @param config            config instance containing at least one {@link String} => {@link Config} mapping
     * @param creatorCustomizer creator customizer {@link BiFunction}; this function is called before constructing
     *                          actual instance using {@link InstanceCreator#build()} with a configuration name and
     *                          customizer that can be customized
     * @param <B>               constructed type
     * @param <T>               creator type
     * @return map of {@link String} => created instance mappings
     * @throws RuntimeException if any enabled instance can't be created; in this case all previously created instances,
     *                          if there are any, of course will get closed if they implement {@link java.io.Closeable}
     *                          or {@link AutoCloseable}
     */
    @SneakyThrows
    public <B, T extends InstanceCreator<B>> Map<String, B> createNamed(
        @NonNull Class<T> creatorType,
        @NonNull Config config,
        @NonNull BiFunction<String, T, T> creatorCustomizer) {

        val created = new LinkedHashMap<String, B>();
        val errorRef = new AtomicReference<Throwable>();

        config.root().entrySet()
            .stream()
            .map(it -> new SimpleEntry<>(it.getKey(), toConfig(it.getValue())))
            .filter(it -> isEnabledConfig(it.getValue()))
            .sorted(Comparator.comparing(SimpleEntry::getKey))
            .map(it -> {
                val name = it.getKey();
                val cfg = it.getValue();
                try {
                    val instance = createNamedInstance(creatorType, cfg, creatorCustomizer, name);
                    created.put(name, instance);
                    return new SimpleEntry<>(name, instance);
                } catch (Throwable t) {
                    errorRef.compareAndSet(null, t);
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .forEach(it -> log.debug("successfully created named instance: {}", it));

        // did any creation failed?
        if (errorRef.get() != null) {
            // close all successfully created instances so far if they implement AutoCloseable/Closeable
            if (!created.isEmpty()) {
                log.debug("exception and there are created instance: {}", created);
                val c = created.values();
                closeInstances(c);
            }

            // re-throw exception
            throw errorRef.get();
        }

        return created;
    }

    private <B> void closeInstances(Collection<B> c) {
        c.stream()
            .filter(it -> it instanceof Closeable)
            .map(it -> (Closeable) it)
            .forEach(it -> Tsc4jImplUtils.close(it, log));
    }

    private static <B, T extends InstanceCreator<B>> B createNamedInstance(Class<T> creatorType,
                                                                           Config config,
                                                                           BiFunction<String, T, T> creatorCustomizer,
                                                                           String name) {
        val implName = getImplementationName(config)
            .orElseThrow(() ->
                new IllegalArgumentException("Instance creation config '" + name + "' doesn't include implementation name"));
        try {
            val loadedCreator = loadInstanceCreator(creatorType, implName);
            log.debug("[{}/{}] loaded instance creator: {}", creatorType, implName, loadedCreator);

            val configuredCreator = configureInstanceCreator(loadedCreator, config);
            log.debug("[{}/{}] configured instance creator: {}", creatorType, implName, configuredCreator);

            val customizedCreator = customizeInstanceCreator(configuredCreator, name, creatorCustomizer);
            log.debug("[{}/{}] customized instance creator: {}", creatorType, implName, customizedCreator);

            val createdInstance = Objects.requireNonNull(customizedCreator.build(),
                "Instance creator " + customizedCreator + " created null instance!");
            log.debug("[{}/{}] instance creator created: {}", creatorType, implName, createdInstance);

            return createdInstance;
        } catch (Exception e) {
            throw Tsc4jException.of("Error creating instance (implementation: %s, config %s): %s",
                e, implName, name, e.getMessage());
        }
    }

    /**
     * Returns implementation name defined in given config.
     *
     * @param config config
     * @return Optional of implementation name from a given config
     */
    public Optional<String> getImplementationName(@NonNull Config config) {
        return IMPLS.stream()
            .filter(config::hasPath)
            .map(config::getString)
            .map(String::trim)
            .filter(it -> !it.isEmpty())
            .findFirst();
    }

    /**
     * Tells whether given config is perceived as enabled or not. <p>
     *
     * Config is perceived as enabled if config path {@code enabled} exist and it's defined and it's value is {@code
     * true} or it's entirely omitted and {@link #getImplementationName(Config)} returns non-empty value
     *
     * @param config config, might be null
     * @return true/false
     */
    public boolean isEnabledConfig(Config config) {
        if (config == null || config.isEmpty() || !getImplementationName(config).isPresent()) {
            return false;
        }
        return config.hasPath(PATH_ENABLED) ? config.getBoolean(PATH_ENABLED) : true;
    }

    private <B, T extends InstanceCreator<B>> T configureInstanceCreator(@NonNull T creator,
                                                                         @NonNull Config config) {
        log.trace("configuring instance creator {} with config: {}", creator, config);
        creator.withConfig(config);
        return creator;
    }

    private <B, T extends InstanceCreator<B>> T customizeInstanceCreator(
        @NonNull T creator,
        @NonNull String name,
        @NonNull BiFunction<String, T, T> creatorCustomizer) {
        log.trace("customizing instance creator {} with customizer: {}", creator, creatorCustomizer);
        return Objects.requireNonNull(creatorCustomizer.apply(name, creator),
            "Instance creator customizer " + creatorCustomizer + " returned null for creator " + creator);
    }
}
