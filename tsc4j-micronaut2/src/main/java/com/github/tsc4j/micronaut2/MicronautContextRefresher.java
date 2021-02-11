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

package com.github.tsc4j.micronaut2;

import com.github.tsc4j.api.Reloadable;
import com.github.tsc4j.api.ReloadableConfig;
import com.github.tsc4j.core.CloseableInstance;
import com.github.tsc4j.core.Tsc4jImplUtils;
import com.typesafe.config.Config;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.runtime.context.scope.refresh.RefreshEvent;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Function;

/**
 * Micronaut application context refresher. Refreshes application context when configuration gets changed in tsc4j.
 */
@Slf4j
@Context
@Singleton
@Requires(beans = Tsc4jPropertySource.class)
@Requires(property = "tsc4j.micronaut.refresh.enabled", value = "true", defaultValue = "false")
public final class MicronautContextRefresher extends CloseableInstance {
    private final Reloadable<Config> reloadable;
    private final ApplicationContext ctx;

    /**
     * Creates new instance.
     *
     * @param reloadableConfig reloadable config
     * @param ctx              application context
     */
    @Inject
    public MicronautContextRefresher(@NonNull ReloadableConfig reloadableConfig,
                                     @NonNull ApplicationContext ctx) {
        this(reloadableConfig.register(Function.identity()), ctx);
    }

    /**
     * Creates new instance.
     *
     * @param reloadable reloadable of top-level config
     * @param ctx        application context
     */
    protected MicronautContextRefresher(@NonNull Reloadable<Config> reloadable, @NonNull ApplicationContext ctx) {
        this.reloadable = reloadable.register(this::configUpdate);
        this.ctx = ctx;
        log.debug("initialized micronaut application context refresher: {}", this);
    }

    /**
     * Invoked when {@link #reloadable} is updated with new config
     *
     * @param config updated config
     */
    private void configUpdate(Config config) {
        if (config == null) {
            log.debug("config disappeared, not updating application context.");
            return;
        }

        val diff = ctx.getEnvironment().refreshAndDiff();
        log.debug("doRefresh(): maybe refreshing micronaut context environment.");
        if (!diff.isEmpty()) {
            log.debug("refreshed micronaut env diff: {}", diff);
            log.info("{} config changed (diff: {} entries), publishing refresh event.",
                Tsc4jImplUtils.NAME, diff.size());
            ctx.publishEvent(new RefreshEvent());
        }
    }

    @Override
    protected void doClose() {
        reloadable.close();
    }
}
