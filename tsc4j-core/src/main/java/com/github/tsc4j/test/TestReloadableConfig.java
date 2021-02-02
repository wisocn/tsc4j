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

package com.github.tsc4j.test;

import com.github.tsc4j.core.CloseableReloadableConfig;
import com.github.tsc4j.core.ReloadableConfigFactory;
import com.github.tsc4j.core.Tsc4jImplUtils;
import com.github.tsc4j.core.impl.AbstractReloadableConfig;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Synchronized;
import lombok.val;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * {@link com.github.tsc4j.api.ReloadableConfig} implementation that allows manual updates of assigned configuration.
 */
public class TestReloadableConfig extends AbstractReloadableConfig {
    /**
     * NOOP on {@link #close()} runnable.
     */
    protected static final Runnable NOOP_ON_CLOSE = () -> {
    };

    /**
     * Test application name  (value: <b>{@value}</b>)
     *
     * @see #fromFactory()
     */
    private static final String TEST_APP_NAME = "application";

    /**
     * Default test environment name (value: <b>{@value}</b>)
     *
     * @see #fromFactory(String)
     */
    private static final String TEST_ENV_NAME = "test";

    private final AtomicBoolean reverseUpdateOrder = new AtomicBoolean();

    /**
     * Custom action to be invoked when {@link #close()} is called
     */
    private final Runnable onClose;

    /**
     * First fetched config.
     */
    private volatile Config origConfig;

    /**
     * Previous config
     *
     * @see #setPreviousConfig()
     */
    private volatile Config previousConfig = null;

    /**
     * Creates new instance without assigning any config instance.
     *
     * @param configSupplier {@link Config} supplier
     * @param onClose        runnable to be invoked when {@link #close()} is called.
     */
    protected TestReloadableConfig(@NonNull Supplier<Config> configSupplier,
                                   @NonNull Runnable onClose) {
        super(configSupplier, false);
        this.onClose = onClose;
        refresh().thenAccept(cfg -> this.origConfig = cfg);
    }

    /**
     * Creates instance with configuration supplier that returns given {@link Config} instance on refresh.
     *
     * @param config config instance
     * @return reloadable config
     * @see #fromSupplier(Supplier)
     * @see #refresh()
     */
    public static TestReloadableConfig fromConfig(@NonNull Config config) {
        return fromSupplier(() -> config);
    }

    /**
     * Creates instance with configuration supplier that parses given map on refresh.
     *
     * @param configMap map containing configuration values.
     * @return reloadable config
     * @see #fromSupplier(Supplier)
     * @see #refresh()
     */
    public static TestReloadableConfig fromMap(@NonNull Map<String, Object> configMap) {
        return fromSupplier(() -> ConfigFactory.parseMap(configMap));
    }

    /**
     * Creates instance with configuration supplier that parses given file on classpath.
     *
     * @param filename classpath filename.
     * @return reloadable config
     * @see #fromSupplier(Supplier)
     * @see #refresh()
     */
    @SneakyThrows
    public static TestReloadableConfig fromClasspath(@NonNull String filename) {
        return fromSupplier(() -> readFromClasspath(filename));
    }

    /**
     * Reads configuration from filename on classpath.
     *
     * @param filename classpath filename.
     * @return config instance
     */
    @SneakyThrows
    private static Config readFromClasspath(@NonNull String filename) {
        return Optional.ofNullable(TestReloadableConfig.class.getResourceAsStream(filename))
            .map(is -> Tsc4jImplUtils.readConfig(is, filename))
            .orElseThrow(() -> new IllegalArgumentException("File not present on classpath: " + filename));
    }

    /**
     * Creates instance with configuration supplier that parses given filename on refresh.
     *
     * @param filename filename to parse.
     * @return reloadable config
     * @see #fromSupplier(Supplier)
     * @see #refresh()
     */
    public static TestReloadableConfig fromFilename(@NonNull String filename) {
        return fromFile(new File(filename));
    }

    /**
     * Creates instance with configuration supplier that parses given file on refresh.
     *
     * @param file file to parse.
     * @return reloadable config
     * @see #fromSupplier(Supplier)
     * @see #refresh()
     */
    public static TestReloadableConfig fromFile(@NonNull File file) {
        return fromSupplier(() -> {
            if (!(file.exists() && file.canRead() && !file.isDirectory())) {
                throw new IllegalArgumentException("Not a readable file: " + file);
            }
            return ConfigFactory.parseFile(file);
        });
    }

    /**
     * Creates instance with configuration supplier that parses given file on refresh.
     *
     * @param path file to parse.
     * @return reloadable config
     * @see #fromSupplier(Supplier)
     * @see #refresh()
     */
    public static TestReloadableConfig fromPath(@NonNull Path path) {
        return fromFile(path.toFile());
    }

    /**
     * Creates instance with given configuration supplier.
     *
     * @param supplier configuration supplier
     * @return reloadable config
     * @see #fromSupplier(Supplier)
     * @see #refresh()
     */
    public static TestReloadableConfig fromSupplier(@NonNull Supplier<Config> supplier) {
        return new TestReloadableConfig(supplier, NOOP_ON_CLOSE);
    }

    /**
     * Creates new instance that initializes sources and transformers by loading tsc4j bootstrap config, with given
     * application name {@value TEST_APP_NAME} and activated environment name {@value #TEST_ENV_NAME}.
     *
     * @return new test reloadable config with manual refresh.
     * @see ReloadableConfigFactory
     */
    public static TestReloadableConfig fromFactory() {
        return fromFactory(TEST_APP_NAME);
    }

    /**
     * Creates new instance that initializes sources and transformers by loading tsc4j bootstrap config, with given
     * application name and activated environment name {@value #TEST_ENV_NAME}.
     *
     * @param appName application name
     * @return new test reloadable config with manual refresh.
     * @see ReloadableConfigFactory
     */
    public static TestReloadableConfig fromFactory(@NonNull String appName) {
        return fromFactory(appName, TEST_ENV_NAME);
    }

    /**
     * Creates new instance that initializes sources and transformers by loading tsc4j bootstrap config, with given
     * application name and activated environment names.
     *
     * @param appName application name
     * @param envs    activated environment names.
     * @return new test reloadable config with manual refresh.
     * @see ReloadableConfigFactory
     */
    public static TestReloadableConfig fromFactory(@NonNull String appName, @NonNull String... envs) {
        return fromFactory(appName, Arrays.asList(envs));
    }

    /**
     * Creates new instance that initializes sources and transformers by loading tsc4j bootstrap config, with given
     * application name and activated environment names.
     *
     * @param appName application name
     * @param envs    activated environment names
     * @return new test reloadable config with manual refresh.
     * @see ReloadableConfigFactory
     */
    public static TestReloadableConfig fromFactory(@NonNull String appName, @NonNull Collection<String> envs) {
        val rc = ReloadableConfigFactory.defaults()
            .setAppName(appName)
            .setEnvs(new ArrayList<>(envs))
            .noRefresh()
            .setVerboseInit(false)
            .create();

        return new TestReloadableConfig(() -> getConfig(rc), () -> rc.close());
    }

    @SneakyThrows
    private static Config getConfig(CloseableReloadableConfig rc) {
        return rc.refresh()
            .toCompletableFuture()
            .get(AbstractReloadableConfig.GET_SYNC_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    /**
     * Creates instance with configuration supplier that returns empty config.
     *
     * @return reloadable config
     * @see #fromSupplier(Supplier)
     * @see #refresh()
     */
    public static TestReloadableConfig empty() {
        return fromSupplier(ConfigFactory::empty);
    }

    @Override
    protected boolean isReverseUpdateOrder() {
        return reverseUpdateOrder.get();
    }

    /**
     * Tells whether reloadables are updated in reverse update order
     *
     * @param flag true/false
     * @return reference to itself.
     */
    public TestReloadableConfig reverseUpdateOrder(boolean flag) {
        reverseUpdateOrder.set(flag);
        return this;
    }

    /**
     * Assigns new configuration from a map.
     *
     * @param configMap map containing configuration
     * @return reference to itself.
     * @see #set(Config)
     */
    public TestReloadableConfig set(@NonNull Map<String, Object> configMap) {
        return set(ConfigFactory.parseMap(configMap));
    }

    /**
     * Assigns new configuration and stores currently assigned configuration so that it can be assigned later via {@link
     * #setPreviousConfig()}.
     *
     * @param config new configuration.
     * @return reference to itself
     */
    @Synchronized
    public final TestReloadableConfig set(@NonNull Config config) {
        // save current config so that we can store it as previous config later.
        val currentConfig = getCurrentConfig();

        // try to assign new config
        assignConfig(config);

        // remember previous config
        this.previousConfig = currentConfig;
        return this;
    }

    /**
     * Assigns new configuration with given config at specified path.
     *
     * @param path   config path
     * @param config new config value to set at given path
     * @return reference to itself
     */
    public TestReloadableConfig set(@NonNull String path, @NonNull Config config) {
        return set(path, config.root());
    }

    /**
     * Assigns new configuration with given value at specified path.
     *
     * @param path  config path
     * @param value value to assign, may be scalar value, {@link Map}, {@link Config} or {@link ConfigValue}
     * @return reference to itself
     */
    public TestReloadableConfig set(@NonNull String path, @NonNull Object value) {
        return set(path, toConfigValue(value));
    }

    private ConfigValue toConfigValue(@NonNull Object value) {
        if (value instanceof Config) {
            return ((Config) value).root();
        } else if (value instanceof ConfigValue) {
            return (ConfigValue) value;
        } else {
            return ConfigValueFactory.fromAnyRef(value);
        }
    }

    /**
     * Assigns new configuration with given config value at specified path.
     *
     * @param path  config path
     * @param value config value to assign
     * @return reference to itself
     */
    public TestReloadableConfig set(@NonNull String path, @NonNull ConfigValue value) {
        val currentConfig = getCurrentConfig();
        val newConfig = currentConfig.withValue(path, value);
        return set(newConfig);
    }

    /**
     * Assigns new configuration without given path.
     *
     * @param path config path to remove.
     * @return reference to itself
     */
    public TestReloadableConfig remove(@NonNull String path) {
        val newConfig = getCurrentConfig().withoutPath(path);
        return set(newConfig);
    }

    /**
     * Assigns empty configuration.
     *
     * @return reference to itself
     * @see #set(Config)
     */
    public TestReloadableConfig clear() {
        return set(ConfigFactory.empty());
    }

    /**
     * Set previously assigned config.
     *
     * @return reference to itself
     * @throws IllegalStateException if previous config is not set.
     */
    public TestReloadableConfig setPreviousConfig() {
        return set(getPreviousConfig());
    }

    /**
     * Sets originally fetched config.
     *
     * @return reference to itself.
     */
    public TestReloadableConfig setOriginalConfig() {
        return set(this.origConfig);
    }

    @Override
    protected void doClose() {
        super.doClose();
        onClose.run();
    }

    /**
     * Returns current config if it's present, otherwise empty config.
     *
     * @return config
     */
    private Config getCurrentConfig() {
        return isPresent() ? getSync() : ConfigFactory.empty();
    }

    /**
     * Returns previously set config.
     *
     * @return previously set config
     * @throws IllegalStateException if previous config does not exist.
     */
    @Synchronized
    private Config getPreviousConfig() {
        val prevConfig = this.previousConfig;
        if (prevConfig == null) {
            throw new IllegalStateException("Previous config is not set.");
        }
        return prevConfig;
    }
}
