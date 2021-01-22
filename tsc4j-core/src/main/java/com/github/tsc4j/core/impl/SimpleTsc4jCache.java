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

import com.github.tsc4j.core.Tsc4jCache;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple cache implementation.
 *
 * @param <K> primary key type.
 * @param <E> cached value type.
 */
@Slf4j
@RequiredArgsConstructor
public final class SimpleTsc4jCache<K, E> implements Tsc4jCache<K, E> {
    /**
     * Run {@link #maintenance()} after specified number of {@link #get(Object)} or {@link #put(Object, Object)}
     * invocations.
     *
     * @see #maybeRunMaintenance()
     */
    private static final int EXPUNGE_EVERY = 1000;

    private final Map<K, CacheElement<E>> cache = new ConcurrentHashMap<>();

    /**
     * Cache name
     */
    @NonNull
    @Getter(AccessLevel.NONE)
    private final String name;

    /**
     * Cache TTL.
     */
    @Getter(AccessLevel.NONE)
    private final Duration cacheTtl;

    /**
     * Clock used for cache eviction algorithm.
     */
    @NonNull
    @Getter(AccessLevel.NONE)
    private final Clock clock;

    private final AtomicInteger counter = new AtomicInteger();

    /**
     * Creates new instance.
     *
     * @param name     cache name
     * @param cacheTtl cache entry ttl
     */
    public SimpleTsc4jCache(@NonNull String name, @NonNull Duration cacheTtl) {
        this(name, cacheTtl, Clock.systemDefaultZone());
    }

    @Override
    public Optional<E> get(@NonNull K key) {
        maybeRunMaintenance();
        return Optional.ofNullable(cache.get(key))
            .filter(e -> isStillValidEntry(key, e))
            .map(e -> e.value)
            .map(e -> {
                log.trace("{} retrieving element from cache: ({}, {}) -> {}", this, key, e);
                return e;
            });
    }

    @Override
    public Tsc4jCache<K, E> put(@NonNull K key, @NonNull E value) {
        val expiresAt = clock.millis() + cacheTtl.toMillis();
        val elem = new CacheElement<E>(value, expiresAt);
        if (log.isTraceEnabled()) {
            log.trace("{} putting element to cache: {} -> {}", this, key, elem);
        }
        cache.put(key, elem);
        return maybeRunMaintenance();
    }

    @Override
    public Tsc4jCache<K, E> clear() {
        log.debug("{} clearing cache.", this);
        cache.clear();
        return this;
    }

    @Override
    public int size() {
        return cache.size();
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Tells whether specified cache entry is still valid. Entry is removed from cache if it's no longer valid.
     *
     * @param key     cache key
     * @param element cache element
     * @return true if entry is still valid, otherwise false (and entry is removed from cache).
     */
    private boolean isStillValidEntry(K key, CacheElement<E> element) {
        val timestamp = clock.millis();
        val isValid = timestamp < element.expiresAt;
        if (!isValid) {
            log.trace("{} cache element '{}' is expired, removing.", this, key);
            cache.remove(key);
        }
        return isValid;
    }

    /**
     * Maybe runs {@link #maintenance()}.
     *
     * @return reference to itself.
     */
    private SimpleTsc4jCache<K, E> maybeRunMaintenance() {
        val count = counter.incrementAndGet();
        if (count % EXPUNGE_EVERY == 0) {
            maintenance();
        }
        return this;
    }

    /**
     * Performs maintenance on cache and removes expired cache items.
     *
     * @return reference to itself.
     */
    private SimpleTsc4jCache<K, E> maintenance() {
        log.debug("{} performing maintenance.", this);
        cache.forEach(this::isStillValidEntry);
        return this;
    }

    /**
     * Cache element.
     *
     * @param <E> cached element type
     */
    @ToString(doNotUseGetters = true)
    @AllArgsConstructor
    private final static class CacheElement<E> {
        /**
         * Cached value.
         */
        @NonNull
        E value;

        /**
         * UNIX timestamp in milliseconds when cache entry expires.
         */
        long expiresAt;
    }
}
