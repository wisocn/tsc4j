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
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Tsc4j health indicator. Returns {@link org.springframework.boot.actuate.health.Status#UP} if config fetch from
 * {@link ReloadableConfig} succeeds and retrieved config is resolved, otherwise {@link
 * org.springframework.boot.actuate.health.Status#DOWN}.
 */
@Slf4j
@Service
@ConditionalOnMissingBean(Tsc4jHealthIndicator.class)
@ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
@ConditionalOnProperty(value = Constants.PROP_HEALTH_ENABLED, matchIfMissing = false)
class Tsc4jHealthIndicator implements HealthIndicator {
    private static final String ERR_KEY = "error";
    private final ReloadableConfig reloadableConfig;

    /**
     * Creates new instance.
     *
     * @param reloadableConfig reloadable config
     */
    @Autowired
    public Tsc4jHealthIndicator(@NonNull ReloadableConfig reloadableConfig) {
        this.reloadableConfig = reloadableConfig;
    }

    @Override
    public Health health() {
        val builder = new Health.Builder();
        try {
            doHealthCheck(builder);
        } catch (Exception e) {
            log.warn("exception while performing tsc4j health check: {}", e.toString(), e);
            builder.down(e);
        }
        return builder.build();
    }

    private void doHealthCheck(@NonNull Health.Builder builder) {
        val config = reloadableConfig.getSync();
        if (config == null) {
            builder.down().withDetail(ERR_KEY, "reloadable config returned null Config instance.");
            return;
        }

        builder.withDetail("hashcode", config.hashCode());
        builder.withDetail("resolved", config.isResolved());
        builder.withDetail("empty", config.isEmpty());
        builder.withDetail("paths", config.root().size());

        if (!config.isResolved()) {
            builder.down().withDetail(ERR_KEY, "Retrieved config is not resolved.");
        } else {
            builder.up();
        }
    }
}
