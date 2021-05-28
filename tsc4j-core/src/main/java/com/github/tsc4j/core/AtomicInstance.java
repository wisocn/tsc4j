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
import lombok.RequiredArgsConstructor;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.Closeable;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Atomic instance holder.
 *
 * @param <T> stored instance type.
 */
@Slf4j
@RequiredArgsConstructor
public final class AtomicInstance<T> implements Closeable {
    private static final Supplier<?> EMPTY_CREATOR = () -> null;
    private static final Consumer<?> EMPTY_DESTROYER = it -> {
    };

    @NonNull
    private final Supplier<T> creator;

    /**
     * Instance destroyer.
     */
    @NonNull
    private final Consumer<T> destroyer;

    /**
     * Stored instance.
     */
    private volatile T instance;

    /**
     * Creates new atomic instance with given instance creator and no-op instance destroyer.
     */
    @SuppressWarnings("unchecked")
    public AtomicInstance(@NonNull Supplier<T> creator) {
        this(creator, (Consumer<T>) EMPTY_DESTROYER);
    }

    /**
     * Creates new atomic instance with no-op instance creator; make sure that you call {@link #getOrCreate(Supplier)}
     * to obtain the actual instance, because {@link #getOrCreate()} will throw {@link NullPointerException}!.
     */
    @SuppressWarnings("unchecked")
    public AtomicInstance(@NonNull Consumer<T> destroyer) {
        this((Supplier<T>) EMPTY_CREATOR, destroyer);
    }

    /**
     * Creates new atomic instance with no-op instance creator/destroyer; make sure that you call {@link
     * #getOrCreate(Supplier)} to obtain the actual instance, because {@link #getOrCreate()} will throw {@link
     * NullPointerException}!.
     *
     * @see #getOrCreate(Supplier)
     */
    @SuppressWarnings("unchecked")
    public AtomicInstance() {
        this((Supplier<T>) EMPTY_CREATOR, (Consumer<T>) EMPTY_DESTROYER);
    }

    /**
     * Creates and stores instance using instance creator specified at object construction if it's not already present
     * and returns stored instance.
     *
     * @return stored instance
     * @throws NullPointerException if supplier returns {@code null}
     * @see #getOrCreate(Supplier)
     */
    @Synchronized
    public T getOrCreate() {
        return getOrCreate(this.creator);
    }

    /**
     * Creates and stores instance using given supplier if it's not already present and returns stored instance.
     *
     * @param creator instance creator which is invoked if instance is not already present
     * @return stored instance
     * @throws NullPointerException if supplier returns {@code null}
     */
    @Synchronized
    public T getOrCreate(@NonNull Supplier<T> creator) {
        if (instance == null) {
            instance = Objects.requireNonNull(creator.get(), "Instance creator returned null: " + creator);
        }
        return instance;
    }

    /**
     * Retrieves stored instance.
     *
     * @return optional of stored instance, might be empty.
     */
    public Optional<T> get() {
        return Optional.ofNullable(instance);
    }

    /**
     * Clears and destroys underlying instance if it exists.
     *
     * @return reference to itself
     */
    @Synchronized
    public AtomicInstance<T> clear() {
        // fetch and un-assign instance
        val i = this.instance;
        this.instance = null;

        if (i != null) {
            try {
                destroyer.accept(i);
            } catch (Throwable t) {
                log.error("exception while destroying atomic instance {} using destroyer {}", i, destroyer, t);
            }
        }

        return this;
    }

    @Override
    public void close() {
        clear();
    }
}
