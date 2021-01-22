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

package com.github.tsc4j.core;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.ToString;

import java.util.Collection;
import java.util.List;

import static com.github.tsc4j.core.Tsc4jImplUtils.optString;
import static com.github.tsc4j.core.Tsc4jImplUtils.validateString;

/**
 * Configuration fetch query.
 */
@ToString
@EqualsAndHashCode
public final class ConfigQuery {
    /**
     * Application name.
     */
    @Getter
    private final String appName;

    /**
     * Datacenter name.
     *
     * @see #getAvailabilityZone()
     */
    @Getter
    private final String datacenter;

    /**
     * Datacenter availability zone.
     *
     * @see #getDatacenter()
     */
    @Getter
    private final String availabilityZone;

    /**
     * Application enabled environments
     */
    @Getter
    private final List<String> envs;

    /**
     * Creates new instance.
     *
     * @param appName          application name
     * @param datacenter       datacenter name
     * @param availabilityZone availability zone
     * @param envs             environment names
     * @throws NullPointerException     in case of null arguments
     * @throws IllegalArgumentException in case of badly formatted arguments
     */
    @Builder(toBuilder = true)
    public ConfigQuery(
        @NonNull String appName,
        String datacenter,
        String availabilityZone,
        @NonNull @Singular("env") Collection<String> envs) {
        this.appName = Tsc4jImplUtils.sanitizeAppName(appName);
        this.datacenter = nullStr(datacenter, "datacenter name");
        this.availabilityZone = nullStr(availabilityZone, "availability zone");
        this.envs = Tsc4jImplUtils.sanitizeEnvs(envs);
    }

    private static String nullStr(String str, String description) {
        return optString(str)
            .map(e -> validateString(e, description))
            .orElse("");
    }
}
