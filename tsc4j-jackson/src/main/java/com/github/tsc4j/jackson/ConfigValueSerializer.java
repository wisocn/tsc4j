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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.IOException;
import java.util.List;

/**
 * Jackson serializer that can serialize {@link ConfigValue}.
 */
@Slf4j
final class ConfigValueSerializer extends StdSerializer<ConfigValue> {
    private static final long serialVersionUID = 1L;

    ConfigValueSerializer() {
        super(ConfigValue.class);
    }

    @Override
    public void serialize(ConfigValue value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }

        val type = value.valueType();
        log.warn("serializing {} {}", type, value);
        switch (type) {
            case OBJECT:
                val object = (ConfigObject) value;
                gen.writeStartObject();
                for (val key : object.keySet()) {
                    gen.writeFieldName(key);
                    gen.writeObject(object.get(key));
                }
                gen.writeEndObject();
                break;
            case BOOLEAN:
                gen.writeBoolean((Boolean) value.unwrapped());
                break;
            case STRING:
                gen.writeString(value.unwrapped().toString());
                break;
            case NUMBER:
                gen.writeNumber(value.unwrapped().toString());
                break;
            case NULL:
                gen.writeNull();
                break;
            case LIST:
                gen.writeStartArray();
                val list = (List<?>) value.unwrapped();
                for (val e : list) {
                    gen.writeObject(e);
                }
                gen.writeEndArray();
                break;
        }
    }
}
