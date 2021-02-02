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
import lombok.NonNull;
import lombok.SneakyThrows;

import java.io.InputStream;

public final class BadConfigValueDecoder1 implements ConfigValueDecoder<InputStream> {
    private static final Class<?> superClass = loadClass();

    @SneakyThrows
    private static Class<?> loadClass() {
        return Class.forName("totally.unexisting.SuperClass");
    }

    @Override
    public Class<InputStream> forClass() {
        return InputStream.class;
    }

    @Override
    public InputStream decode(@NonNull ConfigValue value) throws RuntimeException {
        throw new UnsupportedOperationException("LOL");
    }
}
