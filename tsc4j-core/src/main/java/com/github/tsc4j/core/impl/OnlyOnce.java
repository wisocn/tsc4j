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

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;


/**
 * Set that invokes consumer if given item is seen for the first time.
 *
 * @param <T> element type
 */
@RequiredArgsConstructor
public final class OnlyOnce<T> {
    private final Set<T> set = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * item consumer to run if given item has been first observed.
     */
    @NonNull
    private final Consumer<T> itemConsumer;

    /**
     * Max items to contain in the set.
     */
    private final int maxItems;

    private final AtomicInteger counter = new AtomicInteger();

    /**
     * Runs item consumer if item has not been seen by this set yet.
     *
     * @param item item
     * @return true if given item has been observed for the first time, otherwise false
     */
    public boolean add(@NonNull T item) {
        return add(item, itemConsumer);
    }

    /**
     * Runs given item consumer if item has not been seen by this set yet.
     *
     * @param item         item
     * @param itemConsumer consumer to run
     * @return true if given item has been observed for the first time, otherwise false
     */
    public boolean add(@NonNull T item, Consumer<T> itemConsumer) {
        maybeRunMaintenance();

        val result = set.add(item);
        if (result) {
            itemConsumer.accept(item);
        }

        return result;
    }

    private void maybeRunMaintenance() {
        if (counter.incrementAndGet() % 100 == 0) {
            runMaintenance();
        }
    }

    private void runMaintenance() {
        if (set.size() > maxItems) {
            clear();
        }
    }

    /**
     * Clears internal set.
     *
     * @return reference to itself.
     */
    public OnlyOnce<T> clear() {
        set.clear();
        return this;
    }
}
