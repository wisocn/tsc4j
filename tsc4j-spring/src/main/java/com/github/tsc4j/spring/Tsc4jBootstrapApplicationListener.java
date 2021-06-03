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
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tsc4j spring early-startup application listener which initializes {@link com.github.tsc4j.api.ReloadableConfig} and
 * registers {@link Tsc4jPropertySource} into {@link ConfigurableEnvironment} as early as possible
 */
@Slf4j
public final class Tsc4jBootstrapApplicationListener implements Ordered, ApplicationListener<ApplicationPreparedEvent> {
    private static final AtomicBoolean registrationAlreadyDone = new AtomicBoolean();

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE;
    }

    /**
     * Tells whether given spring environment is part of spring app boostrap.
     *
     * @param env spring env
     * @return true/false
     */
    private boolean isSpringAppBootstrap(ConfigurableEnvironment env) {
        val cfgName = env.getProperty("spring.config.name", "").trim();
        return cfgName.equalsIgnoreCase("bootstrap");
    }

    @Override
    public void onApplicationEvent(@NonNull ApplicationPreparedEvent event) {
        if (hasRegistrationAlreadyHappened()) {
            return;
        }

        val appCtx = event.getApplicationContext();
        val env = appCtx.getEnvironment();

        if (isBootstrapDebugEnabled(env)) {
            log.info("{}", SpringUtils.debugPropertySources(env));
        }

        if (shouldRegisterPropertySource(appCtx, env)) {
            registerTsc4jPropertySource(env);
        } else {
            log.info("tsc4j-spring early property source registration is disabled.");
        }
    }

    private boolean shouldRegisterPropertySource(ConfigurableApplicationContext appCtx,
                                                 ConfigurableEnvironment env) {
        if (!isTsc4jBootstrapEnabled(env)) {
            log.debug("tsc4j spring early bootstrap is disabled.");
            return false;
        }

        return true;
    }

    /**
     * Initializes {@link com.github.tsc4j.api.ReloadableConfig}, creates {@link Tsc4jPropertySource} and registers it
     * to a given spring environment.
     *
     * @param env spring environment
     */
    private void registerTsc4jPropertySource(ConfigurableEnvironment env) {
        log.info("initializing tsc4j-spring");
        log.debug("trying to register tsc4j-spring property source to spring environment: {}", env);

        val rc = SpringUtils.reloadableConfig(env);
        val propSource = new Tsc4jPropertySource(rc);
        log.debug("created property source: {}" + propSource);

        env.getPropertySources().addLast(propSource);
        log.debug("successfully registered tsc4j-spring property source.");
    }

    /**
     * Used to clear static class state.
     */
    static void clear() {
        registrationAlreadyDone.set(false);
    }

    private static boolean hasRegistrationAlreadyHappened() {
        return !registrationAlreadyDone.compareAndSet(false, true);
    }

    /**
     * Tells whether early tsc4j-spring boostrap is enabled.
     *
     * @param env spring environment
     * @return true/false
     */
    private boolean isTsc4jBootstrapEnabled(ConfigurableEnvironment env) {
        val propName = SpringUtils.propName("bootstrap.enabled");
        return env.getProperty(propName, Boolean.class, true);
    }

    /**
     * Tells whether spring-environment debug is enabled at bootstrap.
     *
     * @param env spring environment
     * @return true/false
     */
    private boolean isBootstrapDebugEnabled(ConfigurableEnvironment env) {
        return env.getProperty(SpringUtils.propName("bootstrap.debug"), Boolean.class, false);
    }
}
