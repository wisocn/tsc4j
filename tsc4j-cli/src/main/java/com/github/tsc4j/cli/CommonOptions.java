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


import com.github.tsc4j.core.Tsc4jConfig;
import com.github.tsc4j.core.Tsc4jImplUtils;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class is meant only as container for parsed command line options.
 */
@Slf4j
@ToString(doNotUseGetters = true)
@Command(mixinStandardHelpOptions = true, versionProvider = VersionProvider.class, sortOptions = false)
final class CommonOptions {
    @Option(names = {"-c", "--config"}, description = Tsc4jImplUtils.NAME + " configuration file")
    private String configFile = null;

    /**
     * Logger log level
     */
    @Getter
    @Option(names = {"-l", "--log-level"}, description = "log level (possible values: error, warn, info, debug, trace)")
    private String logLevel = null;

    /**
     * Quiet execution?
     */
    private boolean quiet = false;

    /**
     * Verbose execution?
     */
    @Option(names = "-v", description = "verbose execution, can be specified multiple times to increase verbosity")
    private List<Boolean> verbosity = new ArrayList<>();

    /**
     * Tells whether stacktrace should be displayed in case of fatal exception.
     */
    @Getter
    @Option(names = {"-S", "--stacktrace"}, description = "display stacktrace in case of exceptions")
    private boolean stacktrace = false;

    private Tsc4jConfig config;

    @Option(names = {"-q", "--quiet"}, description = "quiet execution")
    void setQuiet(boolean flag) {
        quiet = true;
        verbosity = new ArrayList<>();
    }

    @Option(names = {"-D", "--debug"}, description = "use debug log level")
    void setDebug(boolean flag) {
        if (flag) {
            logLevel = "debug";
        }
    }

    /**
     * Tells whether program should output as little output as possible.
     *
     * @return true/false
     * @see #isVerbose()
     */
    public boolean isQuiet() {
        return quiet && !isVerbose();
    }

    /**
     * Tells whether verbose output is enabled
     *
     * @return true/false
     * @see #verbosityLevel()
     * @see #isQuiet()
     */
    public boolean isVerbose() {
        return verbosityLevel() > 0;
    }

    /**
     * Returns verbosity level.
     *
     * @return verbosity level; if 0 verbose output is not enabled.
     * @see #isVerbose()
     */
    public int verbosityLevel() {
        return (int) verbosity.stream().filter(e -> e).count();
    }

    /**
     * Return list of potential tsc4j config file candidates.
     *
     * @return list of config file filenames
     */
    private List<String> configFileCandidates() {
        val home = System.getProperty("user.home");
        return Stream.of(".", home + "/.config", "/usr/local/etc", "/etc")
            .map(e -> e + "/" + Tsc4jImplUtils.NAME + ".conf")
            .collect(Collectors.toList());
    }

    /**
     * Returns tsc4j bootstrap configuration.
     *
     * @return tsc4j configuration
     * @throws RuntimeException if configuration can't be loaded
     * @see #configFile
     * @see #configFileCandidates()
     */
    protected Tsc4jConfig getConfig() {
        if (config == null) {
            config = loadConfig();
        }
        return config;
    }

    private Tsc4jConfig loadConfig() {
        if (configFile != null) {
            log.info("loading {} config file: {}", Tsc4jImplUtils.NAME, configFile);
            return tryLoadConfig(configFile)
                .orElseThrow(() ->
                    new IllegalStateException(String.format("error loading config file: %s", configFile)));
        } else {
            val candidates = configFileCandidates();
            log.debug("trying to load {} configuration from candidate files: {}", Tsc4jImplUtils.NAME, candidates);
            return candidates.stream()
                .map(this::tryLoadConfig)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .orElseThrow(() ->
                    new IllegalStateException(String.format("can't load any of config file candidates: " + candidates)));
        }
    }

    private Optional<Tsc4jConfig> tryLoadConfig(String filename) {
        try {
            return Optional.ofNullable(Tsc4jImplUtils.loadBootstrapConfig(filename));
        } catch (Exception e) {
            log.trace("error loading {} config file {}", Tsc4jImplUtils.NAME, filename, e);
            return Optional.empty();
        }
    }
}
