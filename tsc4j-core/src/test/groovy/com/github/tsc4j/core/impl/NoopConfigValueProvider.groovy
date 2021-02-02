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

import com.github.tsc4j.core.AbstractConfigValueProvider
import com.github.tsc4j.core.ConfigValueProvider
import com.github.tsc4j.core.ValueProviderBuilder
import com.typesafe.config.ConfigValue
import lombok.NonNull

final class NoopConfigValueProvider extends AbstractConfigValueProvider {
    NoopConfigValueProvider() {
        super("noop", true, false)
    }

    static Builder builder() {
        return new Builder()
    }

    @Override
    protected Map<String, ConfigValue> doGet(@NonNull List<String> names) {
        return [:]
    }

    @Override
    String getType() {
        return "noop"
    }

    static final class Builder extends ValueProviderBuilder {
        @Override
        ConfigValueProvider build() {
            new NoopConfigValueProvider()
        }
    }
}
