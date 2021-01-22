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
import com.typesafe.config.Config;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

import java.util.Objects;
import java.util.stream.StreamSupport;

/**
 * Tsc4j configuration class.
 */
@Slf4j
@Configuration
public class Tsc4jConfiguration {
    /**
     * Provides reloadable config singleton.
     *
     * @return reloadable config
     */
    @Bean
    @ConditionalOnMissingBean
    @Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
    public ReloadableConfig reloadableConfig(@NonNull @Value("${spring.application.name}") String appName,
                                             @NonNull Environment env) {
        val rc = SpringUtils.getOrCreateReloadableConfig(appName, env);
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

    @Bean
    @Lazy(value = false)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
    @ConditionalOnMissingBean
    Tsc4jPropertySource tsc4jPropertySource(@NonNull ReloadableConfig reloadableConfig,
                                            @NonNull ConfigurableEnvironment env) {
        val source = new Tsc4jPropertySource(reloadableConfig);

        // if environment doesn't contain this source, add it.
        if (!containsTsc4jSource(env)) {
            env.getPropertySources().addLast(source);
            log.debug("added {} configuration source to spring configurable environment: {}", source, env);
        }

        log.debug("supplying tsc4j property source: {}", source);
        return source;
    }

    private boolean containsTsc4jSource(@NonNull ConfigurableEnvironment env) {
        val sources = env.getPropertySources();
        if (log.isDebugEnabled()) {
            log.debug("configurable environment contains {} property sources.", sources.size());
            sources.forEach(e -> log.debug("  name: '{}' -> {}", e.getName(), e));
        }
        val found = StreamSupport.stream(sources.spliterator(), false)
            .filter(Objects::nonNull)
            .filter(this::isTsc4jPropertySource)
            .findAny();
        log.debug("spring configurable environment contains tsc4j: {}", found.isPresent());
        return found.isPresent();
    }

    private boolean isTsc4jPropertySource(PropertySource<?> source) {
        if (source == null) {
            return false;
        }
        if (source instanceof CompositePropertySource) {
            val composite = (CompositePropertySource) source;
            return composite.getPropertySources().stream().anyMatch(this::isTsc4jPropertySource);
        }
        return source instanceof Tsc4jPropertySource;
    }
}
