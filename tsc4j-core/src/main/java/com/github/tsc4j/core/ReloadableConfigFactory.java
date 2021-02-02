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

import com.github.tsc4j.core.impl.ConfigSupplier;
import com.github.tsc4j.core.impl.DefaultReloadableConfig;
import com.github.tsc4j.core.impl.Stopwatch;
import com.typesafe.config.ConfigFactory;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.github.tsc4j.core.Tsc4jImplUtils.*;

/**
 * {@link com.github.tsc4j.api.ReloadableConfig} factory.
 */
@Slf4j
@Data
@Accessors(chain = true)
public class ReloadableConfigFactory {
    private static final String MY_NAME = ReloadableConfigFactory.class.getSimpleName();

    /**
     * Application name.
     */
    @NonNull
    private String appName = "";

    /**
     * Datacenter name.
     */
    @NonNull
    private String datacenter = "";

    /**
     * Availability zone.
     */
    @NonNull
    private String zone = "";

    /**
     * Runtime environment names.
     */
    @NonNull
    private List<String> envs = new ArrayList<>();

    /**
     * Enable verbose initialization?
     */
    private boolean verboseInit = true;

    /**
     * Tsc4j bootstrap config file path on filesystem or classpath.
     *
     * @see #getBootstrapConfig()
     */
    private String configFile;

    /**
     * Tsc4j configuration instance, takes precedence over {@link #getConfigFile()}.
     *
     * @see #getConfigFile()
     */
    private Tsc4jConfig bootstrapConfig;

    /**
     * Creates builder with autodetected defaults.
     *
     * @return instance builder
     */
    public static ReloadableConfigFactory defaults() {
        val rcf = new ReloadableConfigFactory()
            .setAppName(Tsc4jImplUtils.discoverAppName("application"))
            .setDatacenter(Tsc4jImplUtils.discoverDatacenterName("default"))
            .setZone(Tsc4jImplUtils.discoverAvailabilityZone(""))
            .setEnvs(Tsc4jImplUtils.discoverEnvNames(Collections.singletonList(DEFAULT_ENV_NAME)));

        return rcf;
    }

    /**
     * Sets that configuration should not be automatically refreshed.
     *
     * @return reference to itself
     */
    public ReloadableConfigFactory noRefresh() {
        // set new config without refresh
        this.bootstrapConfig = loadConfig().toBuilder()
            .refreshInterval(Duration.ZERO)
            .build();

        return this;
    }

    private Tsc4jConfig loadConfig() {
        return Optional.ofNullable(bootstrapConfig)
            .map(Optional::of)
            .orElseGet(() -> loadConfigFromFile())
            .orElseGet(() -> Tsc4jImplUtils.loadBootstrapConfig(MY_NAME, getEnvs()));
    }

    private Optional<Tsc4jConfig> loadConfigFromFile() {
        // try to load config from filename defined field
        return loadConfigFromFileInInstanceField()
            .map(Optional::of)
            // try to load config from filename defined system props/env
            .orElseGet(this::loadConfigFromFileFromSysProps);
    }

    private Optional<Tsc4jConfig> loadConfigFromFileInInstanceField() {
        return optString(getConfigFile())
            .map(Tsc4jImplUtils::loadBootstrapConfig);
    }

    private Optional<Tsc4jConfig> loadConfigFromFileFromSysProps() {
        return Tsc4jImplUtils.tsc4jPropValue(Tsc4jImplUtils.PROP_CONFIG)
            .map(file -> Tsc4jImplUtils.loadBootstrapConfig(file));
    }

    /**
     * Creates reloadable config
     *
     * @return reloadable config
     * @throws Tsc4jException in case of instance creation failure
     */
    public CloseableReloadableConfig create() {
        log.debug("creating reloadable config (after tsc4j init: {})", Tsc4jImplUtils.timeSinceInitialization());
        // first load bootstrap config
        val sw = new Stopwatch();
        val config = loadConfig();
        log.debug("loaded {} bootstrap config in {}", NAME, sw);

        val appName = Tsc4jImplUtils.tsc4jPropValue("appname").orElse(this.appName);
        val datacenter = Tsc4jImplUtils.tsc4jPropValue("datacenter").orElse(this.datacenter);
        val zone = Tsc4jImplUtils.tsc4jPropValue("datacenter").orElse(this.zone);
        val envs = Tsc4jImplUtils.tsc4jPropValue("envs")
            .map(Tsc4jImplUtils::splitToUniqueList)
            .orElse(this.envs);

        if (verboseInit) {
            log.info("creating reloadable config for application {}, residing in datacenter {}/{}, running in envs {}",
                appName, datacenter, zone, envs);
        }
        log.debug("using bootstrap config: {}", config);

        // create query
        val query = ConfigQuery.builder()
            .appName(appName)
            .datacenter(datacenter)
            .availabilityZone(zone)
            .envs(envs)
            .build();

        // init config source
        val srcTimer = new Stopwatch();
        val source = Tsc4j.configSource(config, envs, ConfigFactory::empty, ConfigFactory::load);
        log.debug("created config source in {}", srcTimer);

        // create reloadable config
        val configSupplier = new ConfigSupplier(source, query);
        val rc = DefaultReloadableConfig.builder()
            .configSupplier(configSupplier)
            .refreshInterval(config.getRefreshInterval())
            .refreshJitterPct(config.getRefreshIntervalJitterPct())
            .reverseUpdateOrder(config.isReverseUpdateOrder())
            .logFirstFetch(isVerboseInit())
            .build();

        log.debug("created reloadable config in {}: {}", sw, rc);
        return rc;
    }
}
