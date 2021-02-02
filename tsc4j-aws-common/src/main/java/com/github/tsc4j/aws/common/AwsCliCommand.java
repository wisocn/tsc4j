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

package com.github.tsc4j.aws.common;

import com.github.tsc4j.cli.AbstractCommand;
import lombok.AccessLevel;
import lombok.Getter;
import picocli.CommandLine;

/**
 * Abstract class for writing CLI commands that use AWS SDK.
 */
public abstract class AwsCliCommand extends AbstractCommand {
    /**
     * AWS access key, may be null
     */
    @Getter(AccessLevel.PROTECTED)
    @CommandLine.Option(names = {"-a", "--access-key-id"}, description = "AWS access key id")
    private String accessKeyId = null;

    /**
     * AWS secret key, may be null
     */
    @Getter(AccessLevel.PROTECTED)
    @CommandLine.Option(names = {"-s", "--secret-access-key"}, description = "AWS secret access key")
    private String secretAccessKey = null;

    /**
     * Returns AWS IAM ARN for assumeRole, may be null
     */
    @Getter(AccessLevel.PROTECTED)
    @CommandLine.Option(names = {"-r", "--region"}, description = "AWS region")
    private String region = null;

    /**
     * Returns AWS configuration from given command line arguments.
     *
     * @return aws config
     */
    protected final AwsConfig getAwsConfig() {
        return new AwsConfig()
            .setAccessKeyId(getAccessKeyId())
            .setSecretAccessKey(getSecretAccessKey())
            .setRegion(getRegion());
    }

    @Override
    public String getGroup() {
        return "aws";
    }
}
