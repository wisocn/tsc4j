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

package com.github.tsc4j.examples.boot.twodotzero;

import com.github.tsc4j.api.Tsc4jBeanBuilder;
import com.github.tsc4j.api.Tsc4jConfigPath;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Value
@Builder(toBuilder = true)
@Tsc4jBeanBuilder
@Tsc4jConfigPath("my.config")
public class AppConfig {
    @NonNull
    String foo;

    @Builder.Default
    Set<String> stringSet = Collections.emptySet();

    @Builder.Default
    List<String> stringList = Collections.emptyList();
}
