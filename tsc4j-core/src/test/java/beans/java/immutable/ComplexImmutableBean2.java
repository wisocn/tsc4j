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

package beans.java.immutable;

import com.github.tsc4j.api.Tsc4jBeanBuilder;
import com.typesafe.config.Config;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.Map;

@Slf4j
@Value
@Builder(toBuilder = true)
@Tsc4jBeanBuilder
@AllArgsConstructor
public class ComplexImmutableBean2<T> implements Iterable<T> {
    @Singular("platform")
    Map<String, SubBean> platforms;

    @Override
    public Iterator<T> iterator() {
        throw new UnsupportedOperationException("Trololo!");
    }

    @Value
    @Builder(toBuilder = true)
    @Tsc4jBeanBuilder
    @AllArgsConstructor
    public static class SubBean {
        @NonNull
        Map<String, Object> defaults;

        @NonNull
        Config senders;
    }
}
