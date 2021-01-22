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

import java.io.Closeable;

/**
 * Interface that performs transformations on {@link com.typesafe.config.Config} instance.
 */
public interface ConfigTransformer extends Closeable {
    /**
     * Returns transformer type.
     *
     * @return transformer type.
     */
    String getType();

    /**
     * Returns transformer name
     *
     * @return transformer name
     */
    String getName();

    /**
     * Tells whether exceptions thrown by {@link #transform(Config)} should be tolerated.
     *
     * @return true/false
     */
    boolean allowErrors();

    /**
     * Transforms configuration object.
     *
     * @param config configuration object to transform
     * @return transformed configuration object
     */
    Config transform(Config config);

    /**
     * Closes config transformer.
     */
    @Override
    void close();
}
