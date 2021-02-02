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

package com.github.tsc4j.api;

import com.typesafe.config.Config;
import lombok.NonNull;

/**
 * Instances implementing this interface allow their reconfiguration with settings stored in {@link Config} instance.
 *
 * @param <T> instance type
 * @see #withConfig(Config)
 */
@FunctionalInterface
public interface WithConfig<T> {

    /**
     * Applies given configuration to object instance.
     *
     * @param config configuration that applies
     * @return reference to itself
     * @throws NullPointerException in case of null arguments
     * @throws RuntimeException     if exception is thrown while applying configuration
     */
    T withConfig(@NonNull Config config);
}
