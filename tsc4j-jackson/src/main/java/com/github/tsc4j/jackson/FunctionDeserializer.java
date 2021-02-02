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

package com.github.tsc4j.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonTokenId;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.IOException;
import java.util.function.Function;

/**
 * Jackson-databind functional deserializer.
 *
 * @param <T> deserialization class type.
 */
@Slf4j
final class FunctionDeserializer<T> extends StdDeserializer<T> {
    private static final long serialVersionUID = 1L;

    private final Function<ConfigValue, T> converter;

    /**
     * Creates new instance.
     *
     * @param forClass  class that this deserializer is able to deserialize.
     * @param converter converter function that takes json node string and creates instance of {@code forClass} class.
     */
    FunctionDeserializer(@NonNull Class<T> forClass, @NonNull Function<ConfigValue, T> converter) {
        super(forClass);
        this.converter = converter;
    }

    @Override
    public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        val tree = p.getCodec().readTree(p);
        val configValue = toConfigValue(tree);

        if (log.isTraceEnabled()) {
            log.trace("{} tree: {}", this, tree);
            log.trace("{} config value: {}", this, configValue);
        }

        val result = converter.apply(configValue);

        if (log.isTraceEnabled()) {
            if (result == null) {
                log.trace("{}  converted {} to null", this, configValue);
            } else {
                log.trace("{}  converted {} to {} [{}]", this, configValue, result, result.getClass().getName());
            }
        }

        return result;
    }

    private ConfigValue toConfigValue(TreeNode tree) {
        val token = tree.asToken();
        val isString = (token.id() == JsonTokenId.ID_STRING);
        val nodeStr = (isString) ? unescape(tree.toString()) : tree.toString();

        if (log.isTraceEnabled()) {
            log.trace("{}  Xtree: {} [{}]: {}", this, tree, token, nodeStr);
        }

        return ConfigValueFactory.fromAnyRef(nodeStr);
    }

    private String unescape(String str) {
        val chars = str.toCharArray();
        if (chars.length <= 2) {
            return "";
        }
        return new String(chars, 1, chars.length - 2);
    }

    @Override
    public String toString() {
        return "[" + handledType().getName() + "]";
    }
}
