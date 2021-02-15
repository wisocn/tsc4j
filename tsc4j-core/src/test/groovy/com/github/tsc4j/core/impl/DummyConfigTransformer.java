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

package com.github.tsc4j.core.impl;

import com.github.tsc4j.core.AbstractConfigTransformer;
import com.github.tsc4j.core.ConfigTransformer;
import com.github.tsc4j.core.ConfigTransformerBuilder;
import com.typesafe.config.Config;
import lombok.Getter;

public class DummyConfigTransformer extends AbstractConfigTransformer<Void> {
    @Getter
    private final String blah;

    protected DummyConfigTransformer(Builder builder) {
        super(builder);
        this.blah = builder.getBlah();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected Void createTransformationContext(Config config) {
        return null;
    }

    @Override
    public String getType() {
        return "dummy";
    }

    public static class Builder extends ConfigTransformerBuilder<Builder> {
        @Getter
        private String blah = null;

        public Builder setBlah(String blah) {
            if (blah.startsWith("f")) {
                throw new IllegalArgumentException("blah should not start with f");
            }
            this.blah = blah;
            return getThis();
        }

        @Override
        public void withConfig(Config config) {
            super.withConfig(config);

            cfgString(config, "name", this::setBlah);
        }

//        @Override
//        protected Builder checkState() {
//            if (blah == null) {
//                throw new IllegalStateException("blah is not defined.");
//            }
//            return super.checkState();
//        }

        @Override
        public ConfigTransformer build() {
            return new DummyConfigTransformer(this);
        }
    }
}
