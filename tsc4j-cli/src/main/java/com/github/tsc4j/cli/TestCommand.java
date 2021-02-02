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

import com.github.tsc4j.core.ConfigSource;
import com.github.tsc4j.core.Tsc4j;
import com.typesafe.config.Config;
import lombok.Data;
import lombok.NonNull;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * CLI command that allows testing of config values.
 */
@ToString(callSuper = true)
@Slf4j
@Command(description = "Tests configuration for application with specific environments.",
    sortOptions = false)
public final class TestCommand extends ConfigQueryCommand {
    @Option(names = {"-E", "--allow-empty"}, description = "allow empty config?")
    private boolean allowEmptyConfig = false;

    /**
     * Command line parameters.
     */
    @CommandLine.Parameters(description = "application names")
    private List<String> parameters = new ArrayList<>();

    @Override
    public int doCall() {
        val apps = getApps();
        val envs = getEnvs();
        log.warn("test envs: {}", envs);
        log.warn("test apps: {}", apps);

        if (envs.isEmpty()) {
            throw new CommandLine.PicocliException("At least one app environment name needs to be specified.");
        } else if (apps.isEmpty()) {
            throw new CommandLine.PicocliException("At least one app name needs to be specified.");
        }

        val source = configSource();

        // perform tests
        val tasks = envs.stream()
            .flatMap(env -> apps.stream()
                .map(appName -> (Callable<TestResult>) () -> runTest(source, appName, env)))
            .collect(Collectors.toList());
        val results = runTasks(tasks, true);
        val anyFailed = results.stream().anyMatch(TestResult::isError);

        // display results
        envs.stream().sorted().forEach(e -> displayResultsForEnv(e, results));

        return anyFailed ? 1 : 0;
    }

    private void displayResultsForEnv(String envName, List<TestResult> results) {
        getStdout().println(envName);
        getStdout().flush();

        results.stream()
            .filter(e -> e.env().equals(envName))
            .sorted(Comparator.comparing(TestResult::appName))
            .forEach(this::displayTestResult);
    }

    private void displayTestResult(TestResult res) {
        val message = (res.success()) ? "OK" : "ERROR " + res.error();
        getStdout().printf("  %-35s%s\n", res.appName(), message);
        getStdout().flush();
        if (res.success() && isVerbose()) {
            getStderr().println(Tsc4j.render(res.config, verbosityLevel()));
        }
    }

    private TestResult runTest(@NonNull ConfigSource source, @NonNull String appName, @NonNull String env) {
        val query = configQuery(appName)
            .toBuilder()
            .clearEnvs()
            .env(env)
            .build();

        val result = new TestResult(appName, env);
        try {
            val config = source.get(query);

            if (!config.isResolved()) {
                return result.error("config is not resolved");
            }
            if (config.isEmpty() && !allowEmptyConfig) {
                return result.error("empty config");
            }
            return result.config(config);
        } catch (Exception e) {
            val msg = e.getMessage() == null ? e.toString() : e.getMessage();
            return result.error(msg);
        }
    }

    private List<String> getApps() {
        return uniqueList(parameters);
    }

    @Override
    public String getName() {
        return "test";
    }

    @Data
    @Accessors(fluent = true)
    private static class TestResult {
        @NonNull
        String appName;
        @NonNull
        String env;

        Config config;
        String error;

        boolean isError() {
            return !success();
        }

        /**
         * Tells whether result indicates successful test.
         *
         * @return true/false
         */
        boolean success() {
            return config != null;
        }
    }
}
