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

package com.github.tsc4j.core.impl;

import com.github.tsc4j.core.ConfigQuery;
import com.github.tsc4j.core.ConfigSource;
import com.typesafe.config.Config;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;

import java.io.Closeable;
import java.util.function.Supplier;

/**
 * {@link Config} supplier that provides {@link Config} from given {@link ConfigSource} and given {@link ConfigQuery}.
 */
@Value
public final class ConfigSupplier implements Supplier<Config>, Closeable {
    /**
     * Config source.
     */
    @NonNull
    @Getter(AccessLevel.NONE)
    ConfigSource source;

    /**
     * Config query.
     */
    @NonNull
    @Getter(AccessLevel.NONE)
    ConfigQuery query;

    @Override
    public Config get() {
        return source.get(query);
    }

    @Override
    public void close() {
        source.close();
    }
}
