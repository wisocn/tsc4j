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


import com.github.tsc4j.api.Tsc4jBeanBuilder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.List;

@Value
@Builder()
@Tsc4jBeanBuilder
public class ImmutableFoo {
    @NonNull
    String a;

    int b;

    @NonNull
    //@Singular("bar")
        List<ImmutableBar> bars;

    @Value
    @Builder
    @AllArgsConstructor
    @Tsc4jBeanBuilder
    public static class ImmutableBar {
        @Builder.Default
        String x = "a";
        @Builder.Default
        String y = "b";
    }
}
