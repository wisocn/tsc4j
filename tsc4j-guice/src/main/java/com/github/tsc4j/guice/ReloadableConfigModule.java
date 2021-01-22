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

package com.github.tsc4j.guice;


import com.github.tsc4j.api.ReloadableConfig;
import com.github.tsc4j.core.ReloadableConfigFactory;
import com.github.tsc4j.core.Tsc4jImplUtils;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.typesafe.config.Config;
import lombok.Getter;
import lombok.NonNull;

import java.util.Collection;
import java.util.List;

/**
 * {@link ReloadableConfig} guice module.
 */
public class ReloadableConfigModule extends AbstractModule {
    /**
     * Application name.
     */
    @Getter
    private final String appName;

    /**
     * Application datacenter.
     */
    @Getter
    private final String datacenter;

    /**
     * List of configured environment names.
     */
    @Getter
    private final List<String> environments;

    /**
     * Creates new instance.
     *
     * @param appName      application name
     * @param datacenter   datacenter name
     * @param environments collection of environment names
     */
    public ReloadableConfigModule(String appName,
                                  String datacenter,
                                  Collection<String> environments) {
        this.appName = Tsc4jImplUtils.sanitizeAppName(appName);
        this.datacenter = datacenter;
        this.environments = Tsc4jImplUtils.sanitizeEnvs(environments);
    }

    /**
     * Provides {@link ReloadableConfig} singleton.
     *
     * @return reloadable config
     * @throws RuntimeException if reloadable config cannot be created for some reason
     */
    protected ReloadableConfig reloadableConfig() {
        return ReloadableConfigFactory.defaults()
            .setAppName(getAppName())
            .setEnvs(getEnvironments())
            .setDatacenter(getDatacenter())
            .create();
    }

    /**
     * Provides {@link Config} instance.
     *
     * @param rc reloadable config
     * @return current config instance
     */
    @Provides
    public Config config(@NonNull ReloadableConfig rc) {
        return rc.getSync();
    }

    @Override
    protected void configure() {
        bind(ReloadableConfig.class).toInstance(reloadableConfig());
    }
}
