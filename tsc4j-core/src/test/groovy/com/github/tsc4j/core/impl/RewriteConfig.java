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
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Value
@AllArgsConstructor
@Builder(toBuilder = true)
@Tsc4jBeanBuilder
public class RewriteConfig {
    @NonNull
    @Builder.Default
    List<RewriteRule> rules = new ArrayList<>();

    @NonNull
    @Builder.Default
    Set<String> strings = new LinkedHashSet<>();

    @ToString
    @AllArgsConstructor
    @Tsc4jBeanBuilder
    public static class RewriteRule {
        @Getter
        private final Pattern pattern;
        @Getter
        private final String replacement;

        @Builder
        public RewriteRule(@NonNull String pattern, @NonNull String replacement) {
            this.pattern = compilePattern(pattern);
            this.replacement = replacement;
        }

        private Pattern compilePattern(String pattern) {
            return Pattern.compile(pattern);
        }
    }
}

