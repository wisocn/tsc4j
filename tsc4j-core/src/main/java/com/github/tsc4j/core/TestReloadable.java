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

/**
 * Reloadable implementation for testing.
 *
 * @deprecated use {@link com.github.tsc4j.test.TestReloadable}.
 */
@Deprecated
public final class TestReloadable<T> extends com.github.tsc4j.test.TestReloadable {
    /**
     * Creates new reloadable without initial value
     *
     * @deprecated use com.github.tsc4j.test.{@link com.github.tsc4j.test.TestReloadable#TestReloadable()}
     */
    public TestReloadable() {
        super();
    }

    /**
     * Creates new reloadable with initial value
     *
     * @param initialValue
     * @deprecated use com.github.tsc4j.test.{@link com.github.tsc4j.test.TestReloadable#TestReloadable(Object)}
     */
    @SuppressWarnings("unchecked")
    public TestReloadable(T initialValue) {
        super(initialValue);
    }
}
