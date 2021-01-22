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

import com.github.tsc4j.api.Ordered;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import lombok.NonNull;

/**
 * Mapper that converts {@link Config} or {@link ConfigValue} to a custom bean instance.
 */
public interface BeanMapper extends Ordered, Comparable<BeanMapper> {
    /**
     * Creates instance of {@code clazz} from contents of config value(s) at given config path.
     *
     * @param clazz  bean class to instantiate
     * @param config config instance
     * @param path   config path, may be empty to represent root path
     * @param <T>    bean class type
     * @return configured bean instance
     * @throws RuntimeException if bean can't be instantiated or config doesn't contain value at given path
     */
    <T> T create(@NonNull Class<T> clazz, @NonNull Config config, @NonNull String path);

    /**
     * Creates instance of {@code clazz} from contents of given {@link Config} instance at root path.
     *
     * @param clazz  bean class to instantiate
     * @param config config instance
     * @param <T>    bean class type
     * @return configured bean instance
     * @throws RuntimeException if bean can't be instantiated or config doesn't contain value at given path
     */
    default <T> T create(@NonNull Class<T> clazz, @NonNull Config config) {
        return create(clazz, config, "");
    }

    /**
     * Tries to instantiate bean of given type from given config value.
     *
     * @param clazz bean class to instantiate.
     * @param value config value to configure bean from
     * @param path  {@link Config} path at which given {@code value} was retrieved.
     * @param <T>   bean class type
     * @return configured bean instance
     * @throws RuntimeException if bean can't be instantiated
     */
    <T> T create(@NonNull Class<T> clazz, @NonNull ConfigValue value, @NonNull String path);

    @Override
    default int compareTo(BeanMapper o) {
        if (o == null) {
            return 1;
        }
        return Integer.compare(getOrder(), o.getOrder());
    }
}
