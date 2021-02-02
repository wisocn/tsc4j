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


import com.github.tsc4j.core.ConfigQuery;
import com.github.tsc4j.core.ConfigSource;
import com.github.tsc4j.core.Tsc4j;
import com.github.tsc4j.core.Tsc4jImplUtils;
import com.typesafe.config.Config;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.val;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.util.ArrayList;
import java.util.List;

import static com.github.tsc4j.core.Tsc4jImplUtils.optString;

@ToString(callSuper = true)
abstract class ConfigQueryCommand extends AbstractCommand {
    @Option(names = {"-e", "--envs"}, description = "application environments", split = ",", arity = "1..*")
    private List<String> envs = new ArrayList<>();

    /**
     * Datacenter name.
     */
    @Getter(AccessLevel.PROTECTED)
    @Option(names = {"-d", "--datacenter"}, description = "application datacenter (default: \"${DEFAULT-VALUE}\")")
    private String datacenter = "";

    /**
     * Availability zone name.
     */
    @Getter(AccessLevel.PROTECTED)
    @Option(names = {"-z", "--zone"}, description = "availability zone name (default: \"${DEFAULT-VALUE}\")")
    private String availabilityZone = "";

    @Option(names = {"-p", "--path"}, description = "display only specified config path")
    private String path = null;

    /**
     * Returns application unique environments.
     *
     * @return list of application environments
     */
    protected List<String> getEnvs() {
        val list = Tsc4jImplUtils.toUniqueList(envs);
        if (list.isEmpty()) {
            throw new CommandLine.PicocliException("At least one application environment needs to be specified.");
        }
        return list;
    }

    /**
     * Returns config source.
     *
     * @return aggregated config source created from bootstrap config returned by {@link #getConfig()}
     */
    protected ConfigSource configSource() {
        return Tsc4j.configSource(getConfig(), getEnvs());
    }

    /**
     * Creates config query according to given app name.
     *
     * @param appName application name.
     * @return
     */
    protected ConfigQuery configQuery(@NonNull String appName) {
        val result = ConfigQuery.builder()
            .appName(appName)
            .envs(getEnvs())
            .datacenter(getDatacenter())
            .availabilityZone(getAvailabilityZone())
            .build();
        log.debug("created config query for application {}: {}", appName, result);
        return result;
    }

    /**
     * Returns path that is supposed to be rendered.
     *
     * @return path to render
     */
    protected String getPathToRender() {
        return optString(path).orElse("");
    }

    /**
     * Renders given configuration respecting {@link #getPathToRender()} and {@link #verbosityLevel()}.
     *
     * @param config config to render
     * @return rendered config
     */
    protected String renderConfig(@NonNull Config config) {
        return Tsc4j.render(config, getPathToRender(), verbosityLevel()).trim();
    }

    @Override
    public String getGroup() {
        return "000-query";
    }
}
