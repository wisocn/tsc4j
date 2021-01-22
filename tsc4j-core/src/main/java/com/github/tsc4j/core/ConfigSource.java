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

import com.typesafe.config.Config;
import lombok.NonNull;

import java.io.Closeable;

/**
 * Configuration source - retrieves {@link Config} object based on criteria contained in {@link ConfigQuery}.
 *
 * @see #get(ConfigQuery)
 */
public interface ConfigSource extends Closeable {
    /**
     * Tells whether caller should tolerate exceptions thrown by {@link #get(ConfigQuery)}.
     *
     * @return true if config fetch exceptions should be tolerated, otherwise false.
     * @see #get(ConfigQuery)
     */
    boolean allowErrors();

    /**
     * Fetches configuration for given query.
     *
     * @param query configuration query
     * @return fetched configuration
     * @throws NullPointerException in case of null arguments
     * @throws RuntimeException     in case of configuration fetching errors
     * @see #allowErrors()
     */
    Config get(@NonNull ConfigQuery query) throws RuntimeException;

    /**
     * Closes instance and releases any held resources. All methods
     */
    @Override
    void close();
}
