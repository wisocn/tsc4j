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
import com.github.tsc4j.core.Tsc4jLoader;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Sources command
 */
@Slf4j
@CommandLine.Command(description = "Lists available configuration source aliases.", sortOptions = false)
public class SourcesCommand extends AbstractCommand {
    private static final String FMT = "%-20s %-30s %s\n";

    @Override
    public String getName() {
        return "sources";
    }

    @Override
    protected int doCall() {
        return doCall(Tsc4jImplUtils.availableSources());
    }

    protected <T> Integer doCall(@NonNull Collection<Tsc4jLoader<T>> loaders) {
        getStderr().printf(FMT, "name", "aliases", "description");
        loaders.forEach(this::printLoader);
        return 0;
    }

    private void printLoader(Tsc4jLoader<?> e) {
        val aliasStr = e.aliases().stream().collect(Collectors.joining(", "));
        getStdout().printf(FMT, e.name(), aliasStr, e.description());
    }

    @Override
    public String getGroup() {
        return "zzz-misc";
    }
}
