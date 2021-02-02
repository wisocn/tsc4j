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

package decoders;

import com.github.tsc4j.api.ConfigValueDecoder;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;
import lombok.NonNull;
import lombok.val;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class MyConfigValueDecoder1 implements ConfigValueDecoder<InputStream> {
    @Override
    public Class<InputStream> forClass() {
        return InputStream.class;
    }

    @Override
    public InputStream decode(@NonNull ConfigValue value) throws RuntimeException {
        if (value.valueType() != ConfigValueType.STRING) {
            throw new IllegalArgumentException("Unsupported config value type: " + value.valueType());
        }
        val s = value.unwrapped().toString();
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }
}
