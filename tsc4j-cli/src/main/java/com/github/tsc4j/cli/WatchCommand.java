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

import com.github.tsc4j.api.Reloadable;
import com.github.tsc4j.api.ReloadableConfig;
import com.github.tsc4j.core.ReloadableConfigFactory;
import com.github.tsc4j.core.Tsc4j;
import com.github.tsc4j.core.Tsc4jConfig;
import com.github.tsc4j.core.Tsc4jImplUtils;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CLI command that allows retrieval of config values.
 */
@Slf4j
@Command(description = "Watches configuration for application with specific environments for changes in real-time.",
    sortOptions = false)
public final class WatchCommand extends ConfigQueryCommand {
    @Option(names = {"-a", "--app"}, description = "application name", required = true)
    private String appName = null;

    @Option(names = {"-r", "--refresh-interval"}, description = "configuration refresh interval (default: \"${DEFAULT-VALUE}\")")
    private String refreshDuration = "10s";

    @CommandLine.Parameters(description = "config paths to watch")
    List<String> watchPaths = new ArrayList<>();

    @Override
    @SneakyThrows
    protected int doCall() {
        val watchPaths = getWatchPaths();

        // create reloadable config
        val rc = ReloadableConfigFactory.defaults()
            .setAppName(appName)
            .setEnvs(getEnvs())
            .setDatacenter(getDatacenter())
            .setBootstrapConfig(getConfig())
            .create();

        // register reloadables
        val realoadables = watchPaths.stream()
            .map(path -> watchPath(rc, path))
            .collect(Collectors.toList());

        // register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(rc::close));

        // wait for sigint
        boolean doSleep = true;
        while (doSleep) {
            Thread.sleep(100);
        }
        return 0;
    }

    private List<String> getWatchPaths() {
        val result = Tsc4jImplUtils.toUniqueList(this.watchPaths);
        return result.isEmpty() ? Collections.singletonList("") : result;
    }

    @Override
    protected Tsc4jConfig getConfig() {
        val name = "refresh";
        val cfg = ConfigFactory.parseMap(Collections.singletonMap(name, refreshDuration));
        val duration = cfg.getDuration(name);

        return super.getConfig()
            .toBuilder()
            .refreshInterval(duration)
            .refreshIntervalJitterPct(0)
            .build();
    }

    private Reloadable<ConfigValue> watchPath(@NonNull ReloadableConfig rc, @NonNull String path) {
        log.info("watching config path for changes: {}", path);
        return createReloadable(rc, path)
            .ifPresentAndRegister(value -> {
                if (value == null) {
                    getStdout().println("# path: \"" + path + "\": value disappeared.");
                } else {
                    val rendered = value.render(Tsc4j.renderOptions(verbosityLevel())).trim();
                    getStdout().println("# path: \"" + path + "\":\n" + rendered);
                }
            });
    }

    private Reloadable<ConfigValue> createReloadable(@NonNull ReloadableConfig rc, @NonNull String path) {
        return path.isEmpty() ?
            rc.register(cfg -> cfg.root())
            :
            rc.register(path, ConfigValue.class);

    }

    @Override
    protected int verbosityLevel() {
        return super.verbosityLevel() + 1;
    }

    @Override
    public String getName() {
        return "watch";
    }
}
