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

import java.util.Optional;
import java.util.Set;

/**
 * tsc4j implementation loader interface for loading which implementation are meant to be discovered by
 * {@link java.util.ServiceLoader} mechanism.
 *
 * @param <T> implementation type
 */
public interface Tsc4jLoader<T> extends Comparable<Tsc4jLoader> {
    /**
     * Tells whether this loader is able to bootstrap implementation with given name that implements/extends class
     * returned {@link #forClass()} via {@link #getBuilder()} builder instance.
     *
     * @param implementation implementation name
     * @return true/false
     * @see #aliases()
     */
    default boolean supports(@NonNull String implementation) {
        val impl = implementation.trim().toLowerCase();
        return name().equals(impl) || aliases().contains(impl) ||
            forClass().getName().equalsIgnoreCase(impl) ||
            forClass().getSimpleName().equalsIgnoreCase(impl);
    }

    /**
     * Returns implementation primary name.
     *
     * @return implementation primary name.
     * @see #supports(String)
     */
    String name();

    /**
     * Returns set of name aliases for which {@link #supports(String)} must return {@code true}.
     *
     * @return set of implementation names
     * @see #supports(String)
     */
    Set<String> aliases();

    /**
     * Returns class/interface for which this loader provides bootstrap builder.
     *
     * @return implementation class
     * @see #getBuilder()
     */
    Class<T> forClass();

    /**
     * Creates new instance builder.
     *
     * @return optional of instance builder.
     * @see #forClass()
     * @see #getInstance()
     */
    Optional<AbstractBuilder<T, ?>> getBuilder();

    /**
     * Returns instance of class returned by {@link #forClass()}, clients should call this method before {@link
     * #getBuilder()} method.
     *
     * @return optional of instance
     * @see #forClass()
     * @see #getBuilder()
     */
    Optional<T> getInstance();

    /**
     * Returns description for this loader.
     *
     * @return description
     */
    String description();

    /**
     * Defines sort order.
     *
     * @return priority as number, lower number means higher priority
     */
    default int getPriority() {
        return 0;
    }
}
