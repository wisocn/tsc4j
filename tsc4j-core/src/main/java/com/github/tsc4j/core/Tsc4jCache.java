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
 * Tsc4j cache interface.
 *
 * @param <K> key type
 * @param <E> value type
 */
public interface Tsc4jCache<K, E> {
    /**
     * Retrieves entry.
     *
     * @param key cache key
     * @return optional of cached entry.
     * @throws NullPointerException in case of null argument
     */
    Optional<E> get(@NonNull K key);

    /**
     * Adds/replaces entry in the cache.
     *
     * @param key   key
     * @param value value
     * @return reference to itself
     * @throws NullPointerException in case of null arguments
     */
    Tsc4jCache<K, E> put(@NonNull K key, @NonNull E value);

    /**
     * Removes all entries from the cache.
     *
     * @return reference to itself
     */
    Tsc4jCache<K, E> clear();

    /**
     * Returns number of entries in the cache.
     *
     * @return number of entries in cache
     */
    int size();
}
