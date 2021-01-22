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

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tsc4j.core.AbstractBeanMapper;
import com.github.tsc4j.core.BeanMapper;
import com.github.tsc4j.core.ByClassRegistry;
import com.github.tsc4j.core.Tsc4j;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigValue;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.val;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Jackson-databind based {@link BeanMapper} implementation.
 */
public final class JacksonBeanMapper extends AbstractBeanMapper {
    private static final ConfigRenderOptions RENDER_OPTS = Tsc4j.renderOptions(-1);

    private final ObjectMapper mapper = createObjectMapper();

    @Override
    public int getOrder() {
        return -10_000;
    }

    @Override
    @SneakyThrows
    protected <T> T createBean(@NonNull Class<T> clazz, @NonNull ConfigValue value, @NonNull String path) {
        val json = value.render(RENDER_OPTS);
        if (log.isDebugEnabled()) {
            log.debug("createBean(class: {} path: {}): config value: {}", clazz.getName(), path, value);
            log.debug("  as json: {}", json);
        }

        val result = mapper.readValue(json, clazz);
        log.debug("deserialized {} from: {}", result, json);
        return result;
    }

    private ObjectMapper createObjectMapper() {
        return configureMapper(Jackson.get().copy());
    }

    @SuppressWarnings("unchecked")
    private ObjectMapper configureMapper(ObjectMapper objectMapper) {
        val valConverters = getValueConverters();
        val deserializers = valConverters.entrySet()
            .stream()
            .map(e -> new FunctionDeserializer(e.getKey(), e.getValue()))
            .map(e -> (JsonDeserializer) e)
            .collect(Collectors.toList());
        val serializers = Arrays.<JsonSerializer>asList(new ConfigSerializer(), new ConfigValueSerializer());

        val module = new JacksonTsc4jModule(deserializers, serializers);
        return objectMapper.registerModule(module);
    }

    @Override
    protected ByClassRegistry<Function<ConfigValue, ?>> defaultValueConverters() {
        return super.defaultValueConverters()
            // we don't know how to properly unquote strings :-/
            .remove(String.class);
    }
}
