/*
 * Copyright 2017 - 2019 tsc4j project
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
 *
 */

package com.github.tsc4j.testsupport;

import com.github.tsc4j.core.ConfigQuery;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;

/**
 * Constants used in testing.
 */
@UtilityClass
public class TestConstants {
    public final String defaultAppName = "someAppName";
    public final String defaultDatacenter = "datacenterName";
    public final String defaultZone = "availabilityZoneA";
    public final List<String> defaultEnvs = Collections.unmodifiableList(asList("envB", "envA"));

    public final ConfigQuery defaultConfigQuery = ConfigQuery.builder()
        .appName(defaultAppName)
        .datacenter(defaultDatacenter)
        .availabilityZone(defaultZone)
        .envs(defaultEnvs)
        .build();

    public final String TEST_CFG_STRING = "{a: 42, b: true}";
    public final String TEST_CFG_INVALID_STR = "{\na: 42,\n\n   b: true\n\n//   }\n    ";

    /**
     * Returns test config path
     *
     * @param paths path fragments
     * @return built-in config path
     */
    public String testConfigPath(@NonNull String... paths) {
        val basePath = Optional.ofNullable(TestConstants.class.getResource("/test-configs"))
            .map(URL::getFile)
            .orElse("");
        return basePath + Stream.of(paths).collect(Collectors.joining());
    }
}
