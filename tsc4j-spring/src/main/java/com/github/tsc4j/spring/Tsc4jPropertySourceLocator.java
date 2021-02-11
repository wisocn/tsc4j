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

import com.github.tsc4j.core.Tsc4jImplUtils;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

import java.util.Optional;

@Slf4j
@Order
class Tsc4jPropertySourceLocator implements PropertySourceLocator {
    /**
     * Application name, non-null.
     */
    private final String appName;

    /**
     * default spring environment
     */
    private final ConfigurableEnvironment defaultEnv;

    /**
     * Creates new instance.
     *
     * @param appName application name
     * @param env
     */
    @Autowired
    public Tsc4jPropertySourceLocator(@NonNull @Value("${spring.application.name:defaultAppName}") String appName,
                                      @NonNull ConfigurableEnvironment env) {
        this.appName = Tsc4jImplUtils.sanitizeAppName(appName);
        this.defaultEnv = env;
    }

    @Override
    public PropertySource<?> locate(Environment environment) {
        val env = getEnvironment(environment);
        val envNames = SpringUtils.getTsc4jEnvs(env);
        log.debug("locate(): locating property source for application '{}' with activated profiles: {}",
            appName, envNames);

        log.trace("locate(): creating reloadable config.");
        val reloadableConfig = SpringUtils.rcInstanceHolder().getOrCreate(
            () -> SpringUtils.createReloadableConfig(appName, SpringUtils.getTsc4jEnvs(env)));
        log.debug("locate(): created reloadable config: {}", reloadableConfig);

        val source = new Tsc4jPropertySource(reloadableConfig);
        log.debug("created spring property source: {}", source);
        return source;
    }

    private Environment getEnvironment(Environment environment) {
        val result = Optional.ofNullable(environment).orElse(defaultEnv);
        if (this.defaultEnv != environment) {
            log.debug("getEnvironment(): environments differ:\n  default env: {},\n  passed env: {},\n  equal hc: {}",
                defaultEnv, environment, defaultEnv.hashCode() == environment.hashCode());
        }
        log.debug("getEnvironment(): returning environment: {}", result);
        return result;
    }
}
