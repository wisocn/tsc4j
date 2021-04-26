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


import com.github.tsc4j.core.ConfigQuery;
import com.github.tsc4j.core.ConfigSource;
import com.github.tsc4j.core.ConfigSourceBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.SneakyThrows;

public class BadConfigSource4 implements ConfigSource {
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean allowErrors() {
        return false;
    }

    @Override
    public Config get(ConfigQuery query) throws RuntimeException {
        return ConfigFactory.empty();
    }

    @Override
    public void close() {
    }

    static class Builder extends ConfigSourceBuilder<Builder> {
        @SneakyThrows
        Builder() {
            throw new IllegalAccessException("Hi!");
        }

        @Override
        public String type() {
            return "bad4";
        }

        @Override
        public String description() {
            return null;
        }

        @Override
        public Class<? extends ConfigSource> creates() {
            return BadConfigSource4.class;
        }

        @SneakyThrows
        @Override
        public ConfigSource build() {
            return null;
        }
    }
}
