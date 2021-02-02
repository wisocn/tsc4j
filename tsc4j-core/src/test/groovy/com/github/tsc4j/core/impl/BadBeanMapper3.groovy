/*
 * Copyright 2017 - 2019 tsc4j project
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
 *
 */

package com.github.tsc4j.core.impl

import com.github.tsc4j.core.BeanMapper
import com.typesafe.config.Config
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueFactory
import lombok.NonNull

class BadBeanMapper3 implements BeanMapper {
    BadBeanMapper3() {
        if (1 == 1) {
            throw new Exception("this is weird error")
        }
    }

    @Override
    <T> T create(@NonNull Class<T> clazz, @NonNull Config config, @NonNull String path) {
        create(clazz, ConfigValueFactory.fromAnyRef("foo"))
    }

    @Override
    <T> T create(@NonNull Class<T> clazz, @NonNull ConfigValue value, @NonNull String path) {
        throw new UnsupportedOperationException("not today...")
    }
}
