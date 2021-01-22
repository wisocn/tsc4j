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
import com.github.tsc4j.core.ReloadableConfigFactory;
import com.github.tsc4j.core.Tsc4jImplUtils;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Various spring-related utilities.
 */
@Slf4j
@UtilityClass
class SpringUtils {
    private static volatile ReloadableConfig reloadableConfig;

    /**
     * Translates active spring profiles from the environment to unique list of tsc4j env names.
     *
     * @param env spring environment
     * @return list of tsc4j application environments.
     */
    List<String> getTsc4jEnvs(@NonNull Environment env) {
        List<String> profiles = Arrays.asList(env.getActiveProfiles());
        if (profiles.isEmpty()) {
            log.debug("spring environment doesn't declare active profiles, using default profiles.");
            profiles = Arrays.asList(env.getDefaultProfiles());
        }
        profiles = Tsc4jImplUtils.toUniqueList(profiles);
        if (profiles.isEmpty()) {
            throw new IllegalStateException("No spring application profiles are defined.");
        }
        return Collections.unmodifiableList(profiles);
    }

    /**
     * Creates reloadable configuration singleton if it does not exist, otherwise returns already created singleton.
     *
     * @param appName application name
     * @param env     spring environment
     * @return reloadable config
     * @throws NullPointerException in case of null arguments
     * @throws RuntimeException     in case of reloadable config creation errors
     */
    ReloadableConfig getOrCreateReloadableConfig(@NonNull String appName,
                                                 @NonNull Environment env) {
        return getOrCreateReloadableConfig(appName, getTsc4jEnvs(env));
    }

    /**
     * Creates reloadable configuration singleton if it does not exist, otherwise returns already created singleton.
     *
     * @param appName application name
     * @param envs    application running environments
     * @return reloadable config
     * @throws NullPointerException in case of null arguments
     * @throws RuntimeException     in case of reloadable config creation errors
     */
    ReloadableConfig getOrCreateReloadableConfig(@NonNull String appName,
                                                 @NonNull Collection<String> envs) {
        if (reloadableConfig == null) {
            synchronized (Tsc4jConfiguration.class) {
                if (reloadableConfig == null) {
                    // create rc, but spring doesn't have notion of running datacenter name
                    reloadableConfig = ReloadableConfigFactory.defaults()
                        .setAppName(appName)
                        .setEnvs(new ArrayList<>(envs))
                        .create();
                    log.debug("created reloadable config instance: {}", reloadableConfig);
                    val config = reloadableConfig.getSync();
                    log.trace("fetched initial config: {}", config);
                }
            }
        }
        return reloadableConfig;
    }
}
