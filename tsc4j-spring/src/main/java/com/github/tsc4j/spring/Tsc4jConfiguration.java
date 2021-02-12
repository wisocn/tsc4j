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
import lombok.SneakyThrows;
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

import javax.annotation.PreDestroy;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Tsc4j configuration class.
 */
@Slf4j
@Configuration
public class Tsc4jConfiguration {
//    @Lazy(value = false)
//    @Bean(destroyMethod = "close")
//    @Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
//    @Named(ATOMIC_INSTANCE_NAME)
//    AtomicInstance<CloseableReloadableConfig> reloadableConfigAtomicInstance() {
//        return new AtomicInstance<>(CloseableReloadableConfig::close);
//    }

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
        val rc = SpringUtils.rcInstanceHolder().getOrCreate(
            () -> SpringUtils.createReloadableConfig(appName, SpringUtils.getTsc4jEnvs(env)));

        // fetch config
        rc.getSync();

        // hopeless case...
        if (env instanceof ConfigurableEnvironment) {
            tsc4jPropertySource(rc, (ConfigurableEnvironment) env);
        }

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
    @SneakyThrows
    Tsc4jPropertySource tsc4jPropertySource(@NonNull ReloadableConfig reloadableConfig,
                                            @NonNull ConfigurableEnvironment env) {
        Tsc4jPropertySource propSource = findTsc4jPropSource(env)
            .orElseGet(() -> createAndRegisterTsc4jPropertySource(reloadableConfig, env));

        debugSpringEnvPropSources(env);

        log.debug("supplying tsc4j property source: {}", propSource);
        return propSource;

    }

    private Tsc4jPropertySource createAndRegisterTsc4jPropertySource(ReloadableConfig reloadableConfig,
                                                                     ConfigurableEnvironment env) {
        val propertySource = new Tsc4jPropertySource(reloadableConfig);
        log.debug("{} created tsc4j spring property source: {}", this, propertySource);

        // register created property source to spring environment
        env.getPropertySources().addLast(propertySource);

        return propertySource;
    }

    private Optional<Tsc4jPropertySource> findTsc4jPropSource(@NonNull ConfigurableEnvironment env) {
        debugSpringEnvPropSources(env);
        val sources = env.getPropertySources();
        val tsc4jPropSourceOpt = sources.stream()
            .flatMap(this::expandPropertySources)
            .filter(this::isTsc4jPropertySource)
            .map(it -> (Tsc4jPropertySource) it)
            .findFirst();
        log.debug("spring configurable environment contains tsc4j property source: {}", tsc4jPropSourceOpt);
        return tsc4jPropSourceOpt;
    }

    private Stream<PropertySource<?>> expandPropertySources(PropertySource<?> source) {
        if (source == null) {
            return Stream.empty();
        }

        if (source instanceof CompositePropertySource) {
            val composite = (CompositePropertySource) source;
            return composite.getPropertySources().stream();
        }

        return Stream.of(source);
    }

    private boolean isTsc4jPropertySource(PropertySource<?> source) {
        return source instanceof Tsc4jPropertySource;
    }

    private void debugSpringEnvPropSources(ConfigurableEnvironment env) {
        if (log.isDebugEnabled()) {
            val sources = env.getPropertySources();
            val sb = new StringBuilder();
            sources.forEach(it -> sb.append("  name: '" + it.getName() + "' -> " + it + "\n"));
            log.debug("{} spring configurable environment contains {} property sources:\n  {}",
                this, sources.size(), sb);
        }
    }

    @PreDestroy
    void close() {
        SpringUtils.rcInstanceHolder().close();
    }
}
