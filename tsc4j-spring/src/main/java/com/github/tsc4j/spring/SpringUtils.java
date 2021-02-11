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

import com.github.tsc4j.core.AtomicInstance;
import com.github.tsc4j.core.CloseableReloadableConfig;
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
    private static final AtomicInstance<CloseableReloadableConfig> instanceHolder =
        new AtomicInstance<>(CloseableReloadableConfig::close);

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

    CloseableReloadableConfig createReloadableConfig(@NonNull String appName, @NonNull Collection<String> envs) {
        // create rc, but spring doesn't have notion of running datacenter name
        val rc = ReloadableConfigFactory.defaults()
            .setAppName(appName)
            .setEnvs(new ArrayList<>(envs))
            .create();
        log.debug("created reloadable config instance: {}", rc);
        val config = rc.getSync();
        log.trace("fetched initial config: {}", config);

        return rc;
    }

    AtomicInstance<CloseableReloadableConfig> rcInstanceHolder() {
        return instanceHolder;
    }
}
