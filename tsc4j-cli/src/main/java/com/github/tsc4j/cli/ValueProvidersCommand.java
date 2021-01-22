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

package com.github.tsc4j.cli;

import com.github.tsc4j.core.Tsc4jImplUtils;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

/**
 * Transformers command
 */
@Slf4j
@CommandLine.Command(description = "Lists available value providers.", sortOptions = false)
public final class ValueProvidersCommand extends SourcesCommand {
    @Override
    public String getName() {
        return "value-providers";
    }

    @Override
    public int doCall() {
        return doCall(Tsc4jImplUtils.availableValueProviders());
    }
}
