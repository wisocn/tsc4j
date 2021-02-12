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

package com.github.tsc4j.micronaut2;

import com.github.tsc4j.api.ReloadableConfig;
import com.typesafe.config.Config;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Singleton;

/**
 * Micronaut {@link Factory} that instantiates tsc4j beans.
 */
@Slf4j
@Factory
public class Tsc4jBeanFactory {
    /**
     * Provides reloadable config singleton.
     *
     * @return reloadable config
     */
    @Bean
    @Singleton
    @Context
    public ReloadableConfig reloadableConfig() {
        val rc = Utils.instanceHolder()
            .get()
            .orElseThrow(() -> new IllegalStateException("ReloadableConfig instance has not been initialized."))
            .first();

        // wait for config fetch to complete.
        rc.getSync();

        log.debug("providing micronaut reloadable config singleton: {}", rc);
        return rc;
    }

    /**
     * Provides config prototype instance.
     *
     * @param rc reloadable config
     * @return config instance
     */
    @Bean
    @Prototype
    public Config config(@NonNull ReloadableConfig rc) {
        val config = rc.getSync();
        log.debug("providing micronaut config prototype: {}", config.hashCode());
        return config;
    }
}
