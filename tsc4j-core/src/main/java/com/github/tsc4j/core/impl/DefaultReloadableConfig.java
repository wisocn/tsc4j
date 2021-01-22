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

package com.github.tsc4j.core.impl;

import com.github.tsc4j.core.Tsc4jImplUtils;
import com.typesafe.config.Config;
import lombok.Builder;
import lombok.NonNull;
import lombok.val;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Default {@link com.github.tsc4j.api.ReloadableConfig} implementation.
 */
public final class DefaultReloadableConfig extends AbstractReloadableConfig {
    /**
     * Smallest request interval in milliseconds (value: <b>{@value}</b>)
     */
    private static final long SMALLEST_REFRESH_INTERVAL_MILLIS = 100;

    private final ScheduledFuture<?> refreshTicker;
    private final boolean shutdownScheduledExecutor;
    private final ScheduledExecutorService scheduledExecutor;

    /**
     * Creates new instance.
     *
     * @param configSupplier           {@link Config} supplier
     * @param refreshInterval          configuration refresh interval
     * @param refreshJitterPct         configuration interval jitter percentage hint (must be in bounds of 0 - 100)
     * @param scheduledExecutorService scheduled executor service for running scheduled tasks, may be null
     * @param reverseUpdateOrder       update reloadables in reverse order
     * @param logFirstFetch            log first configuration fetch?
     */
    @Builder
    protected DefaultReloadableConfig(
        @NonNull Supplier<Config> configSupplier,
        @NonNull Duration refreshInterval,
        int refreshJitterPct,
        ScheduledExecutorService scheduledExecutorService,
        boolean reverseUpdateOrder,
        boolean logFirstFetch) {
        super(configSupplier, reverseUpdateOrder, logFirstFetch);

        this.shutdownScheduledExecutor = (scheduledExecutorService != null);

        val refreshMillis = computeRefreshInterval(refreshInterval, refreshJitterPct);
        this.scheduledExecutor = getOrCreateScheduledExecutor(scheduledExecutorService, refreshMillis);
        this.refreshTicker = init(refreshMillis, this.scheduledExecutor);
    }

    /**
     * Computes actual refresh interval in milliseconds given refresh interval and refresh jitter percentage.
     *
     * @param refreshInterval refresh interval
     * @param jitterPctHint   refresh interval jitter percentage hint, must be in range of (0 - 100);
     *                        actual jitter percentage will be random value between {@code -jitter} and {@code jitter}.
     * @return refresh interval in milliseconds; always {@code 0} if {@code refreshInterval} is smaller than 1 second or
     *     negative; otherwise result is never smaller than {@value #SMALLEST_REFRESH_INTERVAL_MILLIS} msec.
     */
    protected static long computeRefreshInterval(@NonNull Duration refreshInterval, int jitterPctHint) {
        val interval = refreshInterval.toMillis();
        if (isTooSmallRefreshInterval(interval)) {
            return 0;
        }

        if (jitterPctHint < 0 || jitterPctHint >= 100) {
            throw new IllegalArgumentException(
                "Invalid refresh interval jitter percentage hint (must be in range 0-99): " + jitterPctHint);
        }

        val factor = (Math.random() >= 0.5) ? 1D : -1D;
        val jitterPct = Math.random() * ((double) jitterPctHint) * factor;
        val jitterMillis = (long) ((jitterPct / ((double) 100)) * ((double) interval));

        return Math.max(SMALLEST_REFRESH_INTERVAL_MILLIS, (interval + jitterMillis));
    }

    /**
     * Returns scheduled executor service that will be used to execute periodic tasks.
     *
     * @param executor      user-provided executor service
     * @param refreshMillis refresh interval in milliseconds.
     * @return scheduled executor service; user-provided scheduled executor service if user {@code
     *     executorService} is okay to use, shared executor service if {@code executorService} was {@code
     *     null} and {@code null} if {@code refreshMillis} is smaller than {@value
     *     #SMALLEST_REFRESH_INTERVAL_MILLIS} msec.
     * @throws IllegalArgumentException if user supplied executor was non-null, but unsuitable to use, for example, it's
     *                                  already been shut down.
     */
    private static ScheduledExecutorService getOrCreateScheduledExecutor(ScheduledExecutorService executor,
                                                                         long refreshMillis) {
        if (isTooSmallRefreshInterval(refreshMillis)) {
            return null;
        }

        return Optional.ofNullable(executor)
            .map(Tsc4jImplUtils::checkExecutor)
            .orElseGet(Tsc4jImplUtils::defaultScheduledExecutor);
    }

    /**
     * Tells whether given refresh interval is too small.
     *
     * @param millis refresh interval in millis.
     * @return true/false
     */
    protected static boolean isTooSmallRefreshInterval(long millis) {
        return (millis < SMALLEST_REFRESH_INTERVAL_MILLIS);
    }

    @Override
    protected boolean runRefreshInExecutor() {
        return this.refreshTicker != null;
    }

    private ScheduledFuture<?> init(long refreshMillis, ScheduledExecutorService executor) {
        // don't create refresh ticker, but schedule refresh immediately if refresh interval is to small;
        // user obviously wants to refresh configuration on it's own.
        if (scheduledExecutor == null || isTooSmallRefreshInterval(refreshMillis)) {
            log.info("{} automatic configuration refresh is disabled.", this);
            return null;
        }

        log.info("{} scheduling configuration refresh every {} msec.", this, refreshMillis);
        return executor.scheduleAtFixedRate(this::refresh, 0, refreshMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void doClose() {
        // cancel refresh ticker
        if (refreshTicker != null) {
            if (!refreshTicker.cancel(true)) {
                log.debug("{} error cancelling refresh ticker.", this);
            }
        }

        super.doClose();

        // close scheduled executor if we created it
        if (shutdownScheduledExecutor) {
            Tsc4jImplUtils.shutdownExecutor(scheduledExecutor, 1, TimeUnit.SECONDS);
        }
    }
}
