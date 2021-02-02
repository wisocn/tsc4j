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

import lombok.Getter;
import lombok.NonNull;
import lombok.val;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * Base instance class.
 */
public abstract class BaseInstance extends CloseableInstance {
    /**
     * Instance name.
     */
    @Getter
    private final String name;

    /**
     * {@link #toString()} value
     */
    private final String toString;

    /**
     * Creates instance.
     *
     * @param name instance name
     */
    protected BaseInstance(String name) {
        this.name = Tsc4jImplUtils.validString(name);
        this.toString = createToString();
    }

    /**
     * Creates {@link #toString()} value.
     *
     * @return toString() value
     */
    protected String createToString() {
        val nameStr = Tsc4jImplUtils.optString(getName())
            .map(e -> ", name=" + e)
            .orElse("");
        return String.format("[%s%s]", getType(), nameStr);
    }

    /**
     * Returns instance type.
     *
     * @return instance type.
     */
    public abstract String getType();

    /**
     * Invokes all given tasks ({@link Callable}s) and returns list of execution results.
     *
     * @param callables tasks to run
     * @param parallel  execute tasks in parallel or not?
     * @param <T>       callable return type
     * @return list of callable results
     * @throws Exception if any of tasks throw
     */
    protected final <T> List<T> runTasks(@NonNull Collection<Callable<T>> callables, boolean parallel) {
        return Tsc4jImplUtils.runTasks(callables, parallel);
    }

    /**
     * Removes duplicates from given collection.
     *
     * @param coll collection
     * @return unique list
     */
    protected final List<String> uniqueList(Collection<String> coll) {
        return Tsc4jImplUtils.uniqStream(coll).collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return toString;
    }
}
