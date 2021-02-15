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

import com.github.tsc4j.aws.common.AwsConfig;
import com.github.tsc4j.aws.common.WithAwsConfig;
import com.github.tsc4j.core.AbstractConfigSource;
import com.github.tsc4j.core.ConfigQuery;
import com.github.tsc4j.core.ConfigSource;
import com.github.tsc4j.core.ConfigSourceBuilder;
import com.github.tsc4j.core.Tsc4jImplUtils;
import com.typesafe.config.Config;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.val;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <a href="https://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-paramstore.html">AWS SSM
 * Parameter store</a> {@link ConfigSource} implementation. Fetches parameters from AWS SSM parameter store and builds
 * parameter tree.
 */
public final class ParameterStoreConfigSource extends AbstractConfigSource {
    private final SsmFacade ssm;
    private final List<String> paths;
    private final String atPath;

    /**
     * Creates new instance.
     *
     * @param builder instance builder
     */
    protected ParameterStoreConfigSource(@NonNull Builder builder) {
        super(builder);
        this.ssm = new SsmFacade(this.toString(), builder.getAwsConfig(), true, builder.isParallel());
        this.paths = Tsc4jImplUtils.toUniqueList(builder.getPaths());
        this.atPath = builder.getAtPath();
    }

    /**
     * Creates new instance builder.
     *
     * @return instance builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected List<Config> fetchConfigs(@NonNull ConfigQuery query) {
        val ssmPaths = interpolateVarStrings(paths, query);
        log.debug("{} will fetch parameters using SSM param store paths: {}", this, ssmPaths);
        val params = ssm.fetchByPath(ssmPaths);
        log.trace("{} fetched {} parameters: {}", this, params.size(), params);

        // convert parameters to config an install at correct path
        val config = ssm.toConfig(params);
        val finalConfig = (this.atPath.isEmpty()) ? config : config.atPath(this.atPath);
        log.trace("{} fetched config: {}", this, finalConfig);

        return Collections.singletonList(finalConfig);
    }

    @Override
    protected void doClose() {
        super.doClose();
        ssm.close();
    }

    @Override
    public String getType() {
        return SsmFacade.TYPE;
    }

    /**
     * Builder class for {@link ParameterStoreConfigSource}.
     */
    @Data
    @Accessors(chain = true)
    @EqualsAndHashCode(callSuper = true)
    public static final class Builder extends ConfigSourceBuilder<Builder> implements WithAwsConfig<Builder> {
        @Getter
        private AwsConfig awsConfig = new AwsConfig();

        /**
         * List of AWS SSM parameter store paths to fetch values from. Each path can contain {@link ConfigQuery}
         * magic variables.
         *
         * @see ConfigQuery
         */
        private List<String> paths = new ArrayList<>();

        /**
         * Config path at which SSM parameters will be assigned at final config
         */
        private String atPath = "";

        /**
         * Adds single AWS SSM parameter store path, which might contain {@link ConfigQuery} magic variables.
         *
         * @param path path potentially containing magic variables.
         * @return reference to itself
         * @see ConfigQuery
         * @see #setPaths(List)
         * @see #getPaths()
         */
        public Builder withPath(@NonNull String path) {
            paths.add(path);
            return getThis();
        }

        @Override
        public void withConfig(@NonNull Config config) {
            super.withConfig(config);

            cfgExtract(config, "paths", Config::getStringList, this::setPaths);
            cfgString(config, "at-path", this::setAtPath);
        }

        @Override
        protected Builder checkState() {
            if (getPaths() == null || getPaths().isEmpty()) {
                throw new IllegalStateException("At least one SSM parameter store path should be defined.");
            }
            if (getAtPath() == null) {
                throw new IllegalStateException("Parameter at-path cannot be null.");
            }
            return super.checkState();
        }

        @Override
        public ConfigSource build() {
            return new ParameterStoreConfigSource(this);
        }
    }
}
