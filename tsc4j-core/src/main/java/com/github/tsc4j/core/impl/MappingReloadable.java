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
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.function.Function;

/**
 * Reloadable implementation that maps value of upstream reloadable value into different type using a mapper function.
 *
 * @param <T> upstream reloadable value type
 * @param <R> reloadable value type
 */
@Slf4j
final class MappingReloadable<T, R> extends AbstractReloadable<R> {
    private final Reloadable<T> upstream;
    private final Function<T, R> mapper;

    MappingReloadable(@NonNull Reloadable<T> upstream, @NonNull Function<T, R> mapper) {
        this.mapper = mapper;
        this.upstream = upstream
            .onClear(this::removeValue)
            .ifPresentAndRegister(this::updateValue);
    }

    private void updateValue(T value) {
        // TODO: remove this when `value` will be non-null
        if (value == null) {
            removeValue();
            return;
        }

        val newValue = mapper.apply(value);
        if (newValue == null) {
            removeValue();
        } else {
            setValue(newValue);
        }

        log.trace("{} set new mapped value: {}", this, newValue);
    }

    @Override
    protected void doClose() {
        super.doClose();
        this.upstream.close();
    }
}
