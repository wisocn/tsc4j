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

import lombok.NonNull;

import java.util.Optional;

/**
 * Poor man's mixin for adding cache to the actual implementation
 */
public interface WithCache<K, E> {
    /**
     * Returns the cache.
     *
     * @return cache.
     */
    Tsc4jCache<K, E> getCache();


    /**
     * Retrieves entry from cache.
     *
     * @param key cache key
     * @return optional of cached element.
     */
    default Optional<E> getFromCache(K key) {
        return getCache().get(key);
    }

    /**
     * Stores value to cache for duration specified by cache implementation.
     *
     * @param key   cache key
     * @param value value
     * @return value
     */
    default E putToCache(@NonNull K key, @NonNull E value) {
        getCache().put(key, value);
        return value;
    }
}
