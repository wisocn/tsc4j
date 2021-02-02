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

import com.typesafe.config.ConfigValue;
import lombok.NonNull;

/**
 * Decodes raw {@link ConfigValue} to given class instance.
 *
 * @param <T> class type that can be decoded.
 */
public interface ConfigValueDecoder<T> extends Ordered {
    /**
     * Returns class that this decoder is able to decode.
     *
     * @return class that this config value decoder is able to decode
     */
    Class<T> forClass();

    /**
     * Decodes given config value.
     *
     * @param value config value
     * @return instance of T from given config value
     * @throws RuntimeException if value can't be decoded
     */
    T decode(@NonNull ConfigValue value) throws RuntimeException;
}
