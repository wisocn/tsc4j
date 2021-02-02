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

import com.typesafe.config.ConfigValue;
import lombok.NonNull;
import lombok.val;

import java.io.Closeable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * {@link ConfigValue} provider interface.
 */
public interface ConfigValueProvider extends Closeable {
    /**
     * Returns transformer type.
     *
     * @return transformer type.
     */
    String getType();

    /**
     * Returns transformer name
     *
     * @return transformer name
     */
    String getName();


    /**
     * Tells whether missing values should be tolerated and therefore {@link #get(Collection)} will not throw.
     *
     * @return true/false
     * @see #get(Collection)
     */
    default boolean allowMissing() {
        return false;
    }

    /**
     * Returns {@code name -> value} map for specified value names.
     *
     * @param name value name
     * @return optional of value
     * @see #get(Collection)
     */
    default Optional<ConfigValue> get(@NonNull String name) {
        val map = get(Collections.singletonList(name));
        return Optional.ofNullable(map.get(name));
    }

    /**
     * Returns {@code name -> value} map for specified value names.
     *
     * @param names value names
     * @return map containing values
     * @throws Tsc4jException in case of internal provider error or when {@code names} contains value that cannot be
     *                        found in this value provider and {@link #allowMissing()} returns false.
     * @see #allowMissing()
     */
    Map<String, ConfigValue> get(@NonNull Collection<String> names);

    /**
     * Closes value provider; provider can be no longer used once is closed.
     */
    @Override
    void close();
}
