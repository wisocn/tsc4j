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

package com.github.tsc4j.core;

import com.typesafe.config.Config;
import lombok.Getter;
import lombok.NonNull;

import java.time.Duration;

/**
 * Config source that wraps delegate and caches delegate's responses.
 */
public final class CachedConfigSource implements ConfigSource, WithCache<ConfigQuery, Config> {
    private final ConfigSource delegate;

    @Getter
    private final Tsc4jCache<ConfigQuery, Config> cache;

    /**
     * Creates new instance.
     *
     * @param delegate delegate config source
     * @param cacheTtl cache ttl
     */
    public CachedConfigSource(@NonNull ConfigSource delegate, @NonNull Duration cacheTtl) {
        this(delegate, Tsc4jImplUtils.newCache(delegate.toString(), cacheTtl));
    }

    /**
     * Creates new instance.
     *
     * @param delegate delegate config source
     * @param cache    cache implementation
     */
    protected CachedConfigSource(@NonNull ConfigSource delegate, @NonNull Tsc4jCache<ConfigQuery, Config> cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    @Override
    public boolean allowErrors() {
        return delegate.allowErrors();
    }

    @Override
    public Config get(@NonNull ConfigQuery query) {
        return getFromCache(query)
            .orElseGet(() -> putToCache(query, delegate.get(query)));
    }

    /**
     * Clears the cache.
     *
     * @return reference to itself
     */
    public CachedConfigSource clear() {
        getCache().clear();
        return this;
    }

    /**
     * Returns number of cached items.
     *
     * @return number of cached items.
     */
    public int size() {
        return getCache().size();
    }

    @Override
    public void close() {
        delegate.close();
        cache.clear();
    }
}
