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
import com.github.tsc4j.core.CloseableInstance;
import com.github.tsc4j.core.Tsc4jImplUtils;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Base class for writing {@link Reloadable} aliases
 */
@Slf4j
public abstract class AbstractReloadable<T> extends CloseableInstance implements Reloadable<T> {
    /**
     * List of update consumers.
     */
    private final CopyOnWriteArrayList<Consumer<T>> onUpdate = new CopyOnWriteArrayList<>();
    private final AtomicLong numUpdates = new AtomicLong();

    /**
     * Assigned value.
     */
    private volatile T value;

    @Override
    public final boolean isPresent() {
        return value != null;
    }

    @Override
    public final T get() {
        val value = this.value;
        if (value == null) {
            throw new NoSuchElementException("Value is not present.");
        }
        return value;
    }

    @Override
    public final Reloadable<T> ifPresent(@NonNull Consumer<T> consumer) {
        val value = this.value;
        if (value != null) {
            consumer.accept(value);
        }
        return this;
    }

    @Override
    public final Reloadable<T> register(@NonNull Consumer<T> consumer) {
        checkClosed();
        if (!onUpdate.addIfAbsent(consumer)) {
            log.warn("value update consumer is already registered: {}", consumer);
        }
        return this;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(present=" + isPresent() + ", updates=" + numUpdates + ")";
    }

    /**
     * Sets the new value and runs on-update consumers and increments number of updates only if new value differs from
     * previous one.
     *
     * @param value value to set
     * @return reference to itself
     * @throws NullPointerException  in case of null arguments
     * @throws IllegalStateException if instance is closed.
     */
    protected final Reloadable<T> setValue(@NonNull T value) {
        checkClosed();
        val oldValue = this.value;

        // assign new value only if it differs from previous one
        if (!(oldValue == value || value.equals(oldValue))) {
            this.value = value;
            runOnUpdate();
            log.trace("{} set value to: {}", this, value);
        }

        return this;
    }

    /**
     * Removes value.
     *
     * @return optional of previous value
     */
    protected final Optional<T> removeValue() {
        checkClosed();
        val value = this.value;
        this.value = null;

        // run update consumers only if value was previously present
        if (value != null) {
            runOnUpdate();
            log.debug("{} removed value.", this);
        }
        return Optional.ofNullable(value);
    }

    /**
     * Runs on-update handlers with current assigned value and increments number of updates
     *
     * @see #register(Consumer)
     */
    protected final void runOnUpdate() {
        val value = this.value;
        onUpdate.forEach(consumer -> Tsc4jImplUtils.safeRunnable(() -> consumer.accept(value)).run());
        numUpdates.incrementAndGet();
    }

    /**
     * Tells how many updates has been already done on this reloadable.
     *
     * @return number of updates.
     * @see #setValue(Object)
     */
    public final long getNumUpdates() {
        return numUpdates.get();
    }

    /**
     * Returns list of registered consumers.
     *
     * @return registered consumers
     */
    protected final List<Consumer<T>> registered() {
        return new ArrayList<>(this.onUpdate);
    }

    @Override
    protected void doClose() {
        this.value = null;
        onUpdate.clear();
    }
}
