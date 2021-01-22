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

package com.github.tsc4j.aws.sdk1;

import com.github.tsc4j.aws.common.AwsCliCommand;
import com.github.tsc4j.core.Tsc4j;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;
import picocli.CommandLine.PicocliException;

import java.util.ArrayList;
import java.util.List;

/**
 * CLI command that implements queries to AWS SSM.
 */
@Slf4j
@ToString
@CommandLine.Command(description = "AWS SSM parameter store test command.", sortOptions = false)
public final class AwsSsmCommand extends AwsCliCommand {
    @CommandLine.Parameters(description = "AWS SSM parameter store paths to fetch, can be specified multiple times.")
    private List<String> paths = new ArrayList<>();

    @CommandLine.Option(names = {"-p", "--path"}, description = "render only specified path from fetched config")
    private String renderPath = "";

    @Override
    public int doCall() {
        if (paths.isEmpty()) {
            throw new PicocliException("No AWS SSM paths were specified. Run with --help for instructions.");
        }

        val ssm = new SsmFacade("ssm", getAwsConfig(), true, true);
        val params = ssm.fetchByPath(paths);
        val config = ssm.toConfig(params);
        val renderedConfig = Tsc4j.render(config, renderPath, verbosityLevel() + 1).trim();
        getStdout().println(renderedConfig);
        return 0;
    }

    @Override
    public String getName() {
        return "aws_ssm";
    }

    @Override
    public String getGroup() {
        return "misc";
    }
}
