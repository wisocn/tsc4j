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

import com.github.tsc4j.api.Reloadable;
import com.github.tsc4j.api.ReloadableConfig;
import com.github.tsc4j.core.CloseableInstance;
import com.typesafe.config.Config;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Spring-context refresher that refreshes context when tsc4j config changes.
 */
@Slf4j
@Service
@ConditionalOnMissingBean(Tsc4jSpringContextRefresher.class)
@ConditionalOnClass(ContextRefresher.class)
@ConditionalOnProperty(value = Constants.PROP_REFRESH_CONTEXT, matchIfMissing = true)
class Tsc4jSpringContextRefresher extends CloseableInstance implements EnvironmentAware {
    private final AtomicLong numRefreshes = new AtomicLong();
    private final ContextRefresher contextRefresher;
    private final Reloadable<Config> reloadable;

    /**
     * Creates new spring-context refresher instance.
     *
     * @param contextRefresher context refresher.
     * @param reloadableConfig reloadable config.
     */
    Tsc4jSpringContextRefresher(@NonNull ContextRefresher contextRefresher,
                                @NonNull ReloadableConfig reloadableConfig) {
        this.contextRefresher = contextRefresher;
        this.reloadable = reloadableConfig
            .register(Function.identity())
            .register(this::updateConfig);
    }

    private void updateConfig(Config config) {
        if (config == null) {
            log.debug("{} retrieved null config, doing nothing.", this);
            return;
        } else if (isClosed()) {
            log.debug("{} refusing to refresh spring-context, this refresher is already closed.", this);
            return;
        }

        log.debug("{} triggering spring-context refresh.", this);
        try {
            contextRefresher.refresh();
            numRefreshes.incrementAndGet();
            log.info("spring-context refresh #{} successful.", numRefreshes);
        } catch (Exception e) {
            log.error("exception while trying to refresh spring-context #{}: {}",
                (numRefreshes.get() + 1), e.getMessage(), e);
        }
    }

    @Override
    protected void doClose() {
        reloadable.close();
    }

    @Override
    public void setEnvironment(@NonNull Environment environment) {
        log.debug("{} assigning new environment: {}", this, environment);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(refreshes=" + numRefreshes + ", closed=" + isClosed() + ")";
    }
}
