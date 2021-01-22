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

import com.github.tsc4j.api.Reloadable;
import com.github.tsc4j.api.ReloadableConfig;
import com.github.tsc4j.api.Tsc4jConfigPath;
import com.github.tsc4j.core.CloseableInstance;
import com.github.tsc4j.core.CloseableReloadableConfig;
import com.github.tsc4j.core.Tsc4j;
import com.github.tsc4j.core.Tsc4jException;
import com.github.tsc4j.core.Tsc4jImplUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Synchronized;
import lombok.val;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Base class for implementing {@link ReloadableConfig} implementations.
 */
public abstract class AbstractReloadableConfig extends CloseableInstance implements CloseableReloadableConfig {
    /**
     * Default timeout for configuration retrieval in {@link #getSync()} in milliseconds (value: <b>{@value}</b>)
     *
     * @see #getSync()
     */
    public static long GET_SYNC_TIMEOUT_MILLIS = 30_000;

    /**
     * Empty config.
     */
    private static final Config EMPTY = ConfigFactory.empty();

    /**
     * Instance counter.
     */
    private static final AtomicInteger INSTANCE_ID_COUNTER = new AtomicInteger();

    /**
     * Flag indicating whether current configuration refresh is first one that ever happened in current JVM.
     *
     * @see #onRefreshComplete(Stopwatch, boolean)
     */
    private static final AtomicBoolean FIRST_REFRESH_EVER = new AtomicBoolean();

    /**
     * Instance id.
     */
    @Getter(AccessLevel.PROTECTED)
    private final long instanceId = INSTANCE_ID_COUNTER.incrementAndGet();

    /**
     * Reloadable Id counter.
     */
    private final AtomicLong idCounter = new AtomicLong();

    /**
     * Number of configuration fetches.
     */
    private final AtomicLong numFetches = new AtomicLong();

    /**
     * Number of configuration updates.
     */
    private final AtomicLong numUpdates = new AtomicLong();

    /**
     * Flag that says whether refresh is running.
     */
    private final AtomicBoolean refreshIsRunning = new AtomicBoolean();

    /**
     * Map of registered reloadables.
     */
    private final Map<Long, DefaultReloadable<?>> reloadableMap = new ConcurrentHashMap<>();

    /**
     * {@link Config} supplier used to fetch configuration.
     */
    private final Supplier<Config> configSupplier;

    /**
     * Tells whether reloadables should be updated in reverse order.
     */
    private final boolean reverseUpdateOrder;

    /**
     * Tells whether first fetch should be logged
     */
    private final boolean logFirstFetch;

    /**
     * Completable future containing last fetched {@link Config} instance.
     *
     * @see #getConfigFuture()
     * @see #assignConfigFuture(CompletableFuture)
     */
    private volatile CompletableFuture<Config> configFuture = new CompletableFuture<>();

    /**
     * Creates new instance.
     *
     * @param configSupplier     configuration supplier
     * @param reverseUpdateOrder update reloadables in reverse update order?
     */
    protected AbstractReloadableConfig(Supplier<Config> configSupplier, boolean reverseUpdateOrder) {
        this(configSupplier, reverseUpdateOrder, true);
    }

    /**
     * Creates new instance.
     *
     * @param configSupplier     configuration supplier
     * @param reverseUpdateOrder update reloadables in reverse update order?
     * @param logFirstFetch      log first fetch?
     */
    protected AbstractReloadableConfig(@NonNull Supplier<Config> configSupplier,
                                       boolean reverseUpdateOrder,
                                       boolean logFirstFetch) {
        this.configSupplier = configSupplier;
        this.reverseUpdateOrder = reverseUpdateOrder;
        this.logFirstFetch = logFirstFetch;
    }

    /**
     * Returns next reloadable id.
     *
     * @return next reloadable id
     */
    private long nextId() {
        return idCounter.incrementAndGet();
    }

    /**
     * Tells whether reloadables should be updated in reverse order.
     *
     * @return true/false
     */
    protected boolean isReverseUpdateOrder() {
        return reverseUpdateOrder;
    }

    @Override
    public final boolean isPresent() {
        val future = getConfigFuture();
        return future.isDone() && !future.isCancelled() && !future.isCompletedExceptionally();
    }

    @Override
    public final CompletionStage<Config> get() {
        val future = getConfigFuture();

        if (numFetches.get() < 1) {
            return refresh();
        }

        return future;
    }

    @Override
    public final Config getSync() {
        val timeout = GET_SYNC_TIMEOUT_MILLIS;
        val unit = TimeUnit.MILLISECONDS;
        try {
            return get().toCompletableFuture().get(timeout, unit);
        } catch (InterruptedException e) {
            throw Tsc4jException.of("Interrupted while fetching configuration: %%s", e);
        } catch (ExecutionException e) {
            throw Tsc4jException.of("Error fetching configuration: %%s", e.getCause());
        } catch (TimeoutException e) {
            throw Tsc4jException.of("Configuration fetch timeout exceeded: %d %s", e, timeout, unit);
        }
    }

    @Override
    public final <T> Reloadable<T> register(@NonNull Function<Config, T> converter) {
        return register(ROOT_PATH, converter);
    }

    @Override
    public final <T> Reloadable<T> register(@NonNull String path, @NonNull Class<T> clazz) {
        val beanMapper = Tsc4jImplUtils.beanMapper();
        return register(path, cfg -> beanMapper.create(clazz, cfg, path));
    }

    @Override
    public final <T> Reloadable<T> register(@NonNull Class<T> clazz) {
        return Optional.ofNullable(clazz.getAnnotation(Tsc4jConfigPath.class))
            .flatMap(e -> Tsc4jImplUtils.optString(e.value()))
            .map(path -> register(path, clazz))
            .orElseThrow(() -> new IllegalArgumentException("Class " + clazz.getName() +
                " is not annotated with @" + Tsc4jConfigPath.class.getSimpleName()));
    }

    @Override
    public final CompletionStage<Config> refresh() {
        checkClosed();

        if (!beginRefresh()) {
            log.info("{} another refresh is already running, skipping refresh attempt.", this);
            return get();
        }

        val sw = new Stopwatch();
        try {
            val refreshNum = numFetches.incrementAndGet();
            log.debug("{} triggering configuration refresh #{}", this, refreshNum);
            val future = doRefresh();
            return decorateRefreshFuture(future, sw);
        } catch (Throwable e) {
            val failedFuture = new CompletableFuture<Config>();
            failedFuture.completeExceptionally(e);
            return decorateRefreshFuture(failedFuture, sw);
        }
    }

    private CompletionStage<Config> decorateRefreshFuture(@NonNull CompletionStage<Config> future,
                                                          @NonNull Stopwatch sw) {
        // perform side effect: assign fetched configuration or handle refresh error when given future is completed;
        return future.whenComplete((config, exception) -> finishRefreshAttempt(config, exception, sw));

        //return future;
    }

    /**
     * Finishes configuration refresh attempt.
     *
     * @param config    non-null fetched config if refresh attempt succeeded.
     * @param exception non-null exception if refresh attempt was not successful
     * @param sw        stopwatch for timings.
     */
    private void finishRefreshAttempt(Config config, Throwable exception, @NonNull Stopwatch sw) {
        try {
            val succeeded = (exception == null);
            if (succeeded) {
                if (config == null) {
                    throw new IllegalStateException("Can't complete without error and with null config.");
                } else {
                    assignConfig(config);
                }
            } else {
                onRefreshError(exception);
            }
            onRefreshComplete(sw, succeeded);
        } catch (Throwable t) {
            //log.error("{} error completing config refresh attempt.", this, t);
            log.error("{} error completing config refresh attempt.", this, t);
            //onRefreshError(t);
        } finally {
            endRefresh();
        }
    }

    private Config onRefreshError(@NonNull Throwable exception) {
        val actualException = (exception instanceof ExecutionException || exception instanceof CompletionException) ?
            exception.getCause() : exception;

        val oldFuture = getConfigFuture();
        log.warn("{} error refreshing configuration: {}", this, actualException.getMessage(), actualException);
        if (!oldFuture.isDone()) {
            oldFuture.completeExceptionally(actualException);
        }

        return EMPTY;
    }

    private void onRefreshComplete(@NonNull Stopwatch sw, boolean succeeded) {
        val refreshNum = numFetches.get();
        val isFirst = refreshNum == 1;
        val marker = succeeded ? "succeeded" : "failed";
        val logMessage = "{} config refresh operation #{} {} after {}";
        if (isFirst) {
            if (logFirstFetch) {
                if (FIRST_REFRESH_EVER.compareAndSet(false, true)) {
                    val sinceInit = Tsc4jImplUtils.timeSinceInitialization();
                    log.info("{} first config fetch {} after {} (since init: {})", this, marker, sw, sinceInit);
                } else {
                    log.info("{} first config fetch {} after {}", this, marker, sw);
                }
            }
        } else {
            log.debug(logMessage, this, refreshNum, marker, sw);
        }
    }

    /**
     * Performs configuration refresh.
     *
     * @return completion stage that will be completed with newly fetched {@link Config}
     */
    private CompletionStage<Config> doRefresh() {
        debugStacktrace("running configuration refresh.");

        // create safe runnable that will never throw, instead it will complete given future
        val future = new CompletableFuture<Config>();
        val runnable = createFetchRunnable(future);

        if (runRefreshInExecutor()) {
            Tsc4jImplUtils.defaultExecutor().submit(runnable);
        } else {
            runnable.run();
        }

        return future;
    }

    /**
     * Tells whether configuration refresh should be ran in executor service or directly by the calling thread.
     *
     * @return true/false
     */
    protected boolean runRefreshInExecutor() {
        return false;
    }

    /**
     * Creates safe configuration fetch runnable that never throws.
     *
     * @param future future to complete upon configuration fetch success or failure.
     * @return newly created runnable.
     */
    private Runnable createFetchRunnable(@NonNull CompletableFuture<Config> future) {
        return () -> {
            try {
                val config = configSupplier.get();
                if (config == null) {
                    throw new IllegalStateException("Config supplier " + configSupplier + " returned null.");
                }

                // resolve fetched config
                val resolvedConfig = (config.isResolved()) ? config : Tsc4j.resolveConfig(config);

                future.complete(resolvedConfig);
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        };
    }

    /**
     * Logs stack trace if logger's level is set to debug.
     */
    protected final void debugStacktrace(String message) {
        if (log.isDebugEnabled()) {
            log.debug(message, new RuntimeException("Stacktrace"));
        }
    }


    /**
     * Assigns config instance.
     *
     * @param newConfig new config object.
     * @return {@code newConfig} parameter.
     * @throws NullPointerException     if {@code newConfig} is null
     * @throws IllegalArgumentException if {@code newConfig} is not resolved
     */
    @Synchronized
    protected final Config assignConfig(@NonNull Config newConfig) {
        if (!newConfig.isResolved()) {
            throw new IllegalArgumentException("Configuration is not resolved.");
        }

        val oldConfig = isPresent() ? getSync() : null;
        if (!Tsc4jImplUtils.objectChecksumDiffers(oldConfig, newConfig)) {
            log.debug("{} config checksum doesn't differ to existing one, completing fetch future.", this);
            return newConfig;
        }

        val sw = new Stopwatch();
        val checkSum = Tsc4jImplUtils.objectChecksum(newConfig);

        val oldConfigFuture = getConfigFuture();
        val newConfigFuture = CompletableFuture.completedFuture(newConfig);

        // update reloadables with new config value
        updateReloadables(newConfig);

        // replace config futures with new one
        assignConfigFuture(newConfigFuture);
        log.debug("{} replaced existing config future {} with newly completed config future: {}",
            this, oldConfigFuture, newConfigFuture);

        // also complete old config future if it's not completed already
        if (!oldConfigFuture.isDone()) {
            log.debug("{} completing old config future: {} with new config: {}", this, oldConfigFuture, checkSum);
            oldConfigFuture.complete(newConfig);
        }

        numUpdates.incrementAndGet();
        log.debug("{} assigned new config in {}", this, sw);

        return newConfig;
    }

    /**
     * Creates and registers new reloadable.
     *
     * @param path      config path
     * @param converter config converter
     * @param <T>       reloadable type
     * @return new registered reloadable
     */
    protected final <T> Reloadable<T> register(@NonNull String path, @NonNull Function<Config, T> converter) {
        checkClosed();

        // create new reloadable
        val id = nextId();
        val reloadable = new DefaultReloadable<T>(id, path, converter, this::unregister);

        // apply config immediately if we already have it; this might throw,
        // so we do this before adding reloadable to handlers in order to prevent adding reloadables
        // that consistently throw on every refresh.
        if (isPresent()) {
            log.debug("{} configuration value is present, feeding it to newly created reloadable.", this);
            reloadable.accept(getSync());
        }

        log.debug("{} created new reloadable id {}: {}", this, id, reloadable);

        // register it to handlers
        val oldReloadable = reloadableMap.put(id, reloadable);
        if (oldReloadable != null) {
            log.warn("{} overridden previous reloadable id {}: {} => {}", this, id, oldReloadable, reloadable);
        }

        return reloadable;
    }

    /**
     * Unregisters reloadable from updates
     *
     * @param reloadable reloadable to unregister.
     */
    private void unregister(@NonNull DefaultReloadable<?> reloadable) {
        val id = reloadable.getId();
        val removed = this.reloadableMap.remove(id);
        if (removed == null) {
            log.debug("{} reloadable reloadable was not registered: {}", this, reloadable);
        }
    }

    @Override
    protected void doClose() {
        clear();
        Tsc4jImplUtils.close(configSupplier);
    }

    /**
     * Retrieves all registered reloadables.
     *
     * @return list of reloadables
     */
    private Collection<DefaultReloadable<?>> getReloadables() {
        return reloadableMap.values();
    }

    /**
     * Updates all reloadables with new config.
     *
     * @param newConfig new config
     * @see #updateReloadable(DefaultReloadable, Config)
     */
    private void updateReloadables(@NonNull Config newConfig) {
        sortReloadables(getReloadables())
            .forEach(reloadable -> updateReloadable(reloadable, newConfig));
    }

    /**
     * Sorts reloadables according to their config paths.
     *
     * @param reloadables collection of reloadables
     * @return sorted reloadables
     */
    protected List<DefaultReloadable<?>> sortReloadables(@NonNull Collection<DefaultReloadable<?>> reloadables) {
        if (reloadables.isEmpty()) {
            return Collections.emptyList();
        }

        val result = new ArrayList<DefaultReloadable<?>>(reloadables);
        if (isReverseUpdateOrder()) {
            result.sort(Comparator.reverseOrder());
        } else {
            result.sort(Comparator.naturalOrder());
        }

        return result;
    }

    /**
     * Updates single reloadable with new config.
     *
     * @param reloadable reloadable to update
     * @param newConfig  new config to assign to it.
     */
    private void updateReloadable(@NonNull DefaultReloadable<?> reloadable,
                                  @NonNull Config newConfig) {
        if (reloadable.isClosed()) {
            log.debug("{} refusing to update already closed reloadable: {}", this, reloadable);
            return;
        }

        val sw = new Stopwatch();
        try {
            reloadable.accept(newConfig);
            log.trace("{} updated reloadable in {}: {}", this, sw, reloadable);
        } catch (Throwable t) {
            log.error("{} error updating reloadable (duration: {}) {}: {}", this, sw, reloadable, t.getMessage(), t);
        }
    }

    /**
     * Unregisters all reloadables.
     */
    private void clear() {
        log.debug("{} unregistering all reloadables.", this);
        getReloadables().forEach(Reloadable::close);
        reloadableMap.clear();
    }

    /**
     * Returns number of configuration updates.
     *
     * @return number of updates
     */
    protected final long getNumUpdates() {
        return numUpdates.get();
    }

    /**
     * Returns number of registered reloadables.
     *
     * @return number of registered reloadables
     */
    protected final int size() {
        return reloadableMap.size();
    }

    /**
     * Tells whether refresh is currently running.
     *
     * @return true/false
     */
    protected final boolean isRefreshRunning() {
        return refreshIsRunning.get();
    }

    /**
     * Begin config refresh attempt
     *
     * @return true if refresh can begin, otherwise false
     */
    private boolean beginRefresh() {
        return refreshIsRunning.compareAndSet(false, true);
    }

    /**
     * Mark end of config refresh attempt.
     *
     * @return true if end was marked, otherwise false
     */
    private boolean endRefresh() {
        return refreshIsRunning.compareAndSet(true, false);
    }

    /**
     * Returns current internal config future.
     *
     * @return config future.
     * @throws IllegalStateException in case of internal inconsistencies.
     */
    private CompletableFuture<Config> getConfigFuture() {
        val future = this.configFuture;
        if (future == null) {
            throw new IllegalStateException("Internal config completion stage is null, this is A BUG!!!");
        }
        return future;
    }

    /**
     * Assigns new config future.
     *
     * @param future new config future.
     */
    private void assignConfigFuture(@NonNull CompletableFuture<Config> future) {
        this.configFuture = future;
        log.debug("{} assigned new config future: {}", this, future);
    }

    @Override
    public String toString() {
        return "[id=" + instanceId + ", fetches=" + numFetches + ", updates=" + numUpdates + "]";
    }
}
