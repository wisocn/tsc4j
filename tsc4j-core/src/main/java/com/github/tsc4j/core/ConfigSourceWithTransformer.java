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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * {@link ConfigSource} that encapsulates another {@link ConfigSource} delegate and {@link
 * ConfigTransformer} so that configuration fetched from config source delegate gets
 * transparently transformed.
 */
@Slf4j
@Value
public final class ConfigSourceWithTransformer implements ConfigSource {
    /**
     * Config source delegate.
     */
    @NonNull
    @Getter(AccessLevel.NONE)
    ConfigSource source;

    /**
     * Config transformer delegate
     */
    @NonNull
    @Getter(AccessLevel.NONE)
    ConfigTransformer transformer;

    @Override
    public boolean allowErrors() {
        return false;
    }

    @Override
    public Config get(@NonNull ConfigQuery query) {
        val config = source.get(query);
        return transformer.transform(config);
    }

    @Override
    public void close() {
        Tsc4jImplUtils.close(source, log);
        Tsc4jImplUtils.close(transformer, log);
    }
}
