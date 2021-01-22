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

package com.github.tsc4j.test;

import com.github.tsc4j.api.Reloadable;
import com.github.tsc4j.core.impl.AbstractReloadable;

/**
 * {@link Reloadable} implementation that allows manual value updates and is therefore usable for testing purposes.
 *
 * @param <T> value type
 */
public class TestReloadable<T> extends AbstractReloadable<T> {

    /**
     * Creates new instance without setting initial value.
     */
    public TestReloadable() {
    }

    /**
     * Creates new instance with initial value.
     *
     * @param initialValue initial value
     */
    public TestReloadable(T initialValue) {
        setValue(initialValue);
    }

    /**
     * Creates new instance.
     *
     * @param value initial value, may be null
     * @param <T>   value type
     * @return reloadable containing value if argument is non-null, otherwise empty reloadable.
     */
    public static <T> TestReloadable<T> of(T value) {
        return (value == null) ? new TestReloadable<>() : new TestReloadable<>(value);
    }

    /**
     * Manually sets value.
     *
     * @param value value to set
     * @return reference to itself
     * @throws NullPointerException in case of null arguments
     */
    public TestReloadable<T> set(T value) {
        setValue(value);
        return this;
    }

    /**
     * Removes stored value.
     *
     * @return reference to itself
     */
    public TestReloadable<T> clear() {
        removeValue();
        return this;
    }
}
