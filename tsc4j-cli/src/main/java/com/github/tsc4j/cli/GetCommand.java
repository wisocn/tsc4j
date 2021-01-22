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

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * CLI command that allows retrieval of config values.
 */
@Slf4j
@Command(
    descriptionHeading = "%n",
    parameterListHeading = "%nPARAMETERS:%n",
    optionListHeading = "%nOPTIONS:%n",
    description = "Fetches configuration for application with given  environments.",
    sortOptions = false)
public final class GetCommand extends ConfigQueryCommand {
    @Option(names = {"-a", "--app"}, description = "application name", required = true)
    private String appName = null;

    @Override
    protected boolean isQuiet() {
        return super.isQuiet();
    }

    @Override
    protected int doCall() {
        val configSource = configSource();
        val query = configQuery(appName);
        val config = configSource.get(query);
        getStdout().println(renderConfig(config));
        return 0;
    }

    @Override
    protected int verbosityLevel() {
        return super.verbosityLevel() + 1;
    }

    @Override
    public String getName() {
        return "get";
    }
}
