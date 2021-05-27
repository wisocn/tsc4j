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

package com.github.tsc4j.spring;

import com.github.tsc4j.api.ReloadableConfig;
import com.github.tsc4j.core.CloseableReloadableConfig;
import com.typesafe.config.Config;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.ConfigurableEnvironment;

import javax.annotation.PreDestroy;

/**
 * Tsc4j configuration class.
 */
@Slf4j
@Configuration
public class Tsc4jConfiguration {
    private final CloseableReloadableConfig reloadableConfig;

    /**
     * Creates new instance.
     *
     * @param env spring configurable environment
     */
    @Autowired
    public Tsc4jConfiguration(@NonNull ConfigurableEnvironment env) {
        this.reloadableConfig = SpringUtils.reloadableConfig(env);
        createAndRegisterTsc4jPropertySource(this.reloadableConfig, env);
    }

    /**
     * Provides reloadable config singleton.
     *
     * @return reloadable config
     */
    @Bean
    @ConditionalOnMissingBean
    @Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
    public ReloadableConfig reloadableConfig2() {
        return reloadableConfig;
    }

    private ReloadableConfig reloadableConfig2(@NonNull ConfigurableEnvironment env) {
        val rc = SpringUtils.reloadableConfig(env);

        val propSource = createAndRegisterTsc4jPropertySource(rc, env);
        log.warn("created and registered spring property source: {}", propSource);

        log.debug("supplying reloadable config singleton: {}", rc);
        return rc;
    }

    /**
     * Returns most recent config object fetched from {@link ReloadableConfig} instance.
     *
     * @param rc reloadable config
     * @return config instance
     * @throws RuntimeException when config cannot be fetched from reloadable config
     */
    @Bean
    @ConditionalOnMissingBean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public Config config(@NonNull ReloadableConfig rc) {
        val config = rc.getSync();
        if (config != null) {
            log.debug("supplying config bean (hashcode: {}).", config.hashCode());
        }
        return config;
    }

    private Tsc4jPropertySource createAndRegisterTsc4jPropertySource(ReloadableConfig reloadableConfig,
                                                                     ConfigurableEnvironment env) {
        val propertySource = new Tsc4jPropertySource(reloadableConfig);
        log.debug("{} created tsc4j spring property source: {}", this, propertySource);

        // register created property source to spring environment
        env.getPropertySources().addLast(propertySource);

        return propertySource;
    }

    @PreDestroy
    void close() {
        SpringUtils.instanceHolder().close();
    }
}
