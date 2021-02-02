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

package com.github.tsc4j.api;

import lombok.NonNull;

import java.io.Closeable;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Value from {@link ReloadableConfig} which always holds most up to date value from configuration.
 * <p>
 * Container that contains latest configuration value or custom bean mapped
 *
 * @param <T> value type
 * @see Supplier
 */
public interface Reloadable<T> extends Supplier<T>, Closeable {
    /**
     * Tells whether value is present or not.
     *
     * @return true if reloadable contains value, otherwise false.
     * @see #isEmpty()
     */
    boolean isPresent();

    /**
     * Tells whether reloadable doesn't contain value.
     *
     * @return true if reloadable doesn't contain value, otherwise false.
     * @see #isPresent()
     */
    default boolean isEmpty() {
        return !isPresent();
    }

    /**
     * Retrieves the value if it's present.
     *
     * @return value if present.
     * @throws java.util.NoSuchElementException if value is not present.
     * @see #isPresent()
     * @see #isEmpty()
     */
    @Override
    T get();

    /**
     * Invokes specified consumer if value is present.
     *
     * @param consumer consumer to invoke if value is present
     * @return reference to itself
     * @throws NullPointerException in case of null arguments
     * @see #ifPresentAndRegister(Consumer)
     */
    Reloadable<T> ifPresent(@NonNull Consumer<T> consumer);

    /**
     * Invokes specified consumer if value is present and registers it for value updates.
     * <p><b>NOTE:</b> consumer should be thread-safe and non-blocking.</p>
     *
     * @param consumer consumer to register and invoke if value is present
     * @return reference to itself
     * @throws NullPointerException  in case of null argument(s)
     * @throws IllegalStateException if reloadable is closed
     * @see #ifPresent(Consumer)
     * @see #register(Consumer)
     */
    default Reloadable<T> ifPresentAndRegister(@NonNull Consumer<T> consumer) {
        return ifPresent(consumer).register(consumer);
    }

    /**
     * <p>Adds/registers new consumer that is going to be invoked on value update. Multiple consumers can be registered.
     * Consumer is invoked with newly assigned value when configuration changes; Note that <b>consumer is invoked with
     * <i>null</i> value</b> if value was previously present in reloadable (see {@link #isPresent()}) and has been
     * removed from config during refresh.
     * </p>
     * <p><b>NOTE:</b> consumer should be thread-safe and non-blocking.</p>
     *
     * @param consumer consumer to invoke on value change
     * @return reference to itself
     * @throws NullPointerException  in case of null argument(s)
     * @throws IllegalStateException if reloadable is closed
     */
    Reloadable<T> register(@NonNull Consumer<T> consumer);

    /**
     * Unregisters all value update consumers and unsubscribes itself from value updates.
     */
    @Override
    void close();
}
