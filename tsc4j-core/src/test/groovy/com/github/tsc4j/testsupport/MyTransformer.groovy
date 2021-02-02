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

package com.github.tsc4j.testsupport

import com.github.tsc4j.core.AbstractConfigTransformer
import com.github.tsc4j.core.ConfigTransformer
import com.github.tsc4j.core.ConfigTransformerBuilder
import com.typesafe.config.Config
import lombok.NonNull

class MyTransformer extends AbstractConfigTransformer {
    final int someValue

    protected MyTransformer(Builder builder) {
        super(builder)
        this.someValue = builder.getSomeValue()
    }

    static Builder builder() {
        new Builder()
    }

    @Override
    protected Object createTransformationContext(Config config) {
        null
    }

    @Override
    String getType() {
        return "my"
    }

    static class Builder extends ConfigTransformerBuilder<Builder> {
        int someValue = -1

        @Override
        Builder withConfig(@NonNull Config config) {
            configVal(config, "someValue", { cfg, path -> cfg.getInt(path) }).ifPresent({ someValue = it })
            return super.withConfig(config)
        }

        @Override
        protected Builder checkState() {
            if (someValue < 0 || someValue > 100) {
                throw new IllegalStateException("bad someValue: $someValue")
            }
            return super.checkState()
        }

        @Override
        ConfigTransformer build() {
            new MyTransformer(this)
        }
    }
}
