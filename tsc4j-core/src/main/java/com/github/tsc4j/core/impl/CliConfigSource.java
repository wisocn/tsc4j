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

package com.github.tsc4j.core.impl;

import com.github.tsc4j.core.ConfigQuery;
import com.github.tsc4j.core.ConfigSource;
import com.github.tsc4j.core.Tsc4j;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.typesafe.config.ConfigFactory.empty;

/**
 * {@link ConfigSource} implementation that provides simple config from command line via {@value
 * #PROP_NAME} system property.
 */
@Slf4j
public final class CliConfigSource implements ConfigSource {
    private static final String TYPE = "cli";
    private static final String PROP_NAME = "sun.java.command";
    private static final Pattern splitter = Pattern.compile("\\s+");
    private static final Pattern optPattern = Pattern.compile("^--");
    private static final ConfigSource INSTANCE = new CliConfigSource();

    private final Config config;

    /**
     * Creates new instance.
     *
     * @throws IllegalStateException in case of bad builder state
     */
    protected CliConfigSource() {
        this.config = parseConfig(System.getProperty(PROP_NAME));
    }

    private Config parseConfig(String cliArgsString) {
        if (cliArgsString == null || cliArgsString.isEmpty()) {
            return empty();
        }

        log.trace("{} splitting command line args string: '{}'", this, cliArgsString);
        val argsList = splitter.splitAsStream(cliArgsString.trim())
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        log.debug("{} parsed cli args list: {}", this, argsList);
        if (argsList.isEmpty()) {
            return empty();
        }

        // fill in map
        val cfgMap = new LinkedHashMap<String, String>();

        String path = null;
        boolean nextIsValue = false;
        for (int i = 0; i < argsList.size(); i++) {
            if (nextIsValue) {
                if (!path.isEmpty()) {
                    cfgMap.put(path, argsList.get(i));
                }
                nextIsValue = false;
            } else if (argsList.get(i).startsWith("--")) {
                val s = optPattern.matcher(argsList.get(i)).replaceFirst("");
                val idx = s.indexOf('=');
                if (idx > 0) {
                    val p = Tsc4j.configPath(s.substring(0, idx));
                    val v = s.substring(idx + 1);
                    if (!p.isEmpty()) {
                        cfgMap.put(p, v);
                    }
                } else {
                    path = s;
                    nextIsValue = true;
                }
            }
        }

        log.debug("{} parsed cli config map: {}", this, cfgMap);

        if (cfgMap.isEmpty()) {
            return empty();
        }

        val config = ConfigFactory.parseMap(cfgMap, TYPE);
        log.debug("{} created config specified via command line: {}", this, config);
        return config;
    }

    /**
     * Returns singleton instance.
     *
     * @return singleton instance.
     */
    public static ConfigSource instance() {
        return INSTANCE;
    }

    @Override
    public boolean allowErrors() {
        return false;
    }

    @Override
    public Config get(@NonNull ConfigQuery query) {
        return config;
    }

    @Override
    public void close() {
    }

    public String getType() {
        return TYPE;
    }

    public String getName() {
        return "";
    }

    @Override
    public String toString() {
        return "[" + TYPE + "]";
    }
}
