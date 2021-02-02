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
import lombok.val;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Registry where values are mapped by class/subclass
 *
 * @param <T> stored item type
 */
public final class ByClassRegistry<T> {
    private final Map<Class<?>, T> registry;

    private ByClassRegistry(@NonNull Map<Class<?>, T> source) {
        this.registry = Collections.unmodifiableMap(source);
    }

    /**
     * Creates empty registry.
     *
     * @return empty registry
     * @see #add(Class, Object)
     * @see #remove(Class)
     */
    public static <T> ByClassRegistry<T> empty() {
        return new ByClassRegistry<>(Collections.emptyMap());
    }

    /**
     * Registers new by-class value and returns new registry instance, note that order of adding is important!
     *
     * @param clazz class
     * @param value value tied by class.
     * @return new registry
     */
    public ByClassRegistry<T> add(@NonNull Class<?> clazz, @NonNull T value) {
        val newRegistry = mutableRegistry();
        newRegistry.put(clazz, value);
        return new ByClassRegistry<>(newRegistry);
    }

    /**
     * Creates new registry that contains all entries from this registry with addition of given registry.
     *
     * @param other other registry
     * @return new registry
     */
    public ByClassRegistry<T> add(@NonNull ByClassRegistry<T> other) {
        if (other.isEmpty()) {
            return this;
        }

        val newRegistry = mutableRegistry();
        newRegistry.putAll(other.registry);
        return new ByClassRegistry<>(newRegistry);
    }

    public ByClassRegistry<T> remove(@NonNull Class<?> clazz) {
        val m = mutableRegistry();
        m.remove(clazz);
        return new ByClassRegistry<>(m);
    }

    private Map<Class<?>, T> mutableRegistry() {
        return new LinkedHashMap<>(this.registry);
    }

    public Optional<T> get(@NonNull Class<?> clazz) {
        return getByExactClass(clazz)
            .map(Optional::of)
            .orElseGet(() -> getByNearestClass(clazz));
    }

    /**
     * Tells whether this registry is empty.
     *
     * @return true/false
     */
    public boolean isEmpty() {
        return registry.isEmpty();
    }

    /**
     * Returns number of mappings present in the registry.
     *
     * @return
     */
    public int size() {
        return registry.size();
    }

    /**
     * Returns entry set.
     *
     * @return entry set.
     */
    public Set<Map.Entry<Class<?>, T>> entrySet() {
        return registry.entrySet();
    }

    private Optional<T> getByExactClass(Class<?> clazz) {
        return Optional.ofNullable(registry.get(clazz));
    }

    private Optional<T> getByNearestClass(Class<?> clazz) {
        return registry.entrySet().stream()
            .filter(it -> it.getKey().isAssignableFrom(clazz))
            .map(Map.Entry::getValue)
            .findFirst();
    }
}
