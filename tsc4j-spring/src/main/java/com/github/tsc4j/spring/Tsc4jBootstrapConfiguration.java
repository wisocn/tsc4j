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

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;

import javax.annotation.PreDestroy;
import java.util.Objects;

/**
 * Tsc4j bootstrap configuration.
 */
@Slf4j
@Configuration
public class Tsc4jBootstrapConfiguration {

    /**
     * Configurable spring environment
     */
    @Autowired
    private ConfigurableEnvironment environment = null;

    /**
     * Creates tsc4j property source locator.
     *
     * @param appName application name.
     * @return property source locator
     * @see Constants#PROPERTY_ENABLED
     */
    @Bean
    @ConditionalOnMissingBean(Tsc4jPropertySourceLocator.class)
    @ConditionalOnProperty(value = Constants.PROPERTY_ENABLED, matchIfMissing = true)
    public PropertySourceLocator tsc4jPropertySourceLocator(@Value("${spring.application.name}") String appName,
                                                            @NonNull ApplicationContext appCtx) {
        val env = Objects.requireNonNull(environment, "spring application environment must be injected.");
        log.debug("creating source locator: '{}' with env: {}", appName, env);
        val locator = new Tsc4jPropertySourceLocator(appName, env);
        log.debug("created spring property source locator for app name {}: {}", appName, locator);
        return locator;
    }

    @PreDestroy
    void close() {
        SpringUtils.rcInstanceHolder().close();
    }
}
