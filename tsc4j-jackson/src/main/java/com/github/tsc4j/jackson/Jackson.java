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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Jackson object mapper holder
 */
@UtilityClass
class Jackson {
    private static final ObjectMapper MAPPER = create(true, false);

    /**
     * <p>Returns {@link ObjectMapper} suitable for normal json jobs (strict and non-pretty).</p>
     * <p><b>NOTE:</b>make sure to create a copy if you're planning to change object mapper's configuration!</p>
     *
     * @return object mapper
     */
    public static ObjectMapper get() {
        return MAPPER;
    }

    /**
     * Creates new jackson object mapper with configured annotation support.
     *
     * @param lenient enable relaxed input parsing?
     * @param pretty  enable pretty-printing?
     * @return jackson mapper
     */
    private static ObjectMapper create(boolean lenient, boolean pretty) {
        val mapper = new ObjectMapper()
            //
            // serialization config
            //
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
            .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            //
            // deserialization config
            //
            .configure(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // hocon uses snake-case/kebab-case by default
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.KEBAB_CASE);

        if (lenient) {
            mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
            mapper.configure(JsonParser.Feature.ALLOW_YAML_COMMENTS, true);
            mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
            mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        }

        if (pretty) {
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
        }

        // register other modules
        return mapper.findAndRegisterModules();
    }
}
