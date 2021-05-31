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
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

/**
 * Various {@link Consumer} utilities.
 */
@Slf4j
@UtilityClass
public class Consumers {
    private static final Consumer<?> EMPTY = x -> {
    };

    /**
     * Returns empty consumer.
     *
     * @param <T> consumer type
     * @return consumer
     */
    @SuppressWarnings("unchecked")
    public <T> Consumer<T> empty() {
        return (Consumer<T>) EMPTY;
    }

    /**
     * Safely run given consumer.
     *
     * @param consumer consumer to invoke
     * @param value    value to invoke consumer with
     * @param <T>      value type
     * @return true if consumer invocation was successful, otherwise false.
     */
    public <T> boolean safeRun(@NonNull Consumer<T> consumer, T value) {
        try {
            consumer.accept(value);
            return true;
        } catch (Throwable t) {
            log.error("exception while running consumer {}: {}", consumer, t.getMessage(), t);
        }
        return false;
    }
}
