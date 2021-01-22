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

import com.amazonaws.util.EC2MetadataUtils;
import com.amazonaws.util.json.Jackson;
import com.github.tsc4j.core.AbstractConfigSource;
import com.github.tsc4j.core.ConfigQuery;
import com.github.tsc4j.core.ConfigSource;
import com.github.tsc4j.core.ConfigSourceBuilder;
import com.github.tsc4j.core.Tsc4j;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.val;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static com.typesafe.config.ConfigFactory.empty;

/**
 * EC2 Metadata configuration source.
 *
 * @see <a href="https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-metadata.html">EC2 instance
 *     metadata</a>
 * @see <a href="https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/util/EC2MetadataUtils.html">EC2MetadataUtils</a>
 */
public final class EC2MetadataConfigSource extends AbstractConfigSource {
    /**
     * Default {@link Config} path (value: <b>{@value}</b>)
     *
     * @see #atPath
     */
    protected static final String DEFAULT_CFG_PATH = "aws.ec2.metadata";

    private static final String ORIGIN_DESCRIPTION = "AWS EC2 metadata";
    static final String TYPE = "aws.ec2.metadata";

    /**
     * {@link Config} path to put AWS EC2 metadata to (default: {@value #DEFAULT_CFG_PATH})
     */
    private final String atPath;

    /**
     * Creates new instance
     *
     * @param builder instance builder
     */
    protected EC2MetadataConfigSource(@NonNull Builder builder) {
        super(builder);
        val path = Tsc4j.configPath(builder.getAtPath());
        this.atPath = path.isEmpty() ? DEFAULT_CFG_PATH : path;
    }

    /**
     * Creates instance builder.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    protected List<Config> fetchConfigs(@NonNull ConfigQuery query) {
        val configOpts = Arrays.asList(
            fetchEc2MetadataValue("ami-id", EC2MetadataUtils::getAmiId),
            fetchEc2MetadataValue("ami-launch-index", EC2MetadataUtils::getAmiLaunchIndex),
            fetchEc2MetadataValue("ami-manifest-path", EC2MetadataUtils::getAmiManifestPath),
            fetchEc2MetadataValue("ancestor-ami-ids", EC2MetadataUtils::getAncestorAmiIds),
            fetchEc2MetadataValue("availability-zone", EC2MetadataUtils::getAvailabilityZone),
            fetchEc2MetadataValue("block-device-mapping", EC2MetadataUtils::getBlockDeviceMapping),
            fetchEc2MetadataValue("ec2-instance-region", EC2MetadataUtils::getEC2InstanceRegion),
            fetchEc2MetadataValue("host-address-for-ec2-metadata-service", EC2MetadataUtils::getHostAddressForEC2MetadataService),
            fetchEc2MetadataValue("iam-instance-profile-info", EC2MetadataUtils::getIAMInstanceProfileInfo),
            fetchEc2MetadataValue("iam-security-credentials", EC2MetadataUtils::getIAMSecurityCredentials),
            fetchEc2MetadataValue("instance-action", EC2MetadataUtils::getInstanceAction),
            fetchEc2MetadataValue("instance-id", EC2MetadataUtils::getInstanceId),
            fetchEc2MetadataValue("instance-info", EC2MetadataUtils::getInstanceInfo),
            fetchEc2MetadataValue("instance-type", EC2MetadataUtils::getInstanceType),
            fetchEc2MetadataValue("local-host-name", EC2MetadataUtils::getLocalHostName),
            fetchEc2MetadataValue("mac-address", EC2MetadataUtils::getMacAddress),
            fetchEc2MetadataValue("network-interfaces", EC2MetadataUtils::getNetworkInterfaces),
            fetchEc2MetadataValue("private-ip-address", EC2MetadataUtils::getPrivateIpAddress),
            fetchEc2MetadataValue("product-codes", EC2MetadataUtils::getProductCodes),
            fetchEc2MetadataValue("public-key", EC2MetadataUtils::getPublicKey),
            fetchEc2MetadataValue("ramdisk-id", EC2MetadataUtils::getRamdiskId),
            fetchEc2MetadataValue("reservation-id", EC2MetadataUtils::getReservationId),
            fetchEc2MetadataValue("security-groups", EC2MetadataUtils::getSecurityGroups),
            fetchEc2MetadataValue("user-data", EC2MetadataUtils::getUserData)
        );

        val config = configOpts.stream()
            .filter(Optional::isPresent)
            .map(Optional::get)
            .reduce(empty(), (acc, cur) -> cur.withFallback(acc));

        val result = atPath.isEmpty() ? config : config.atPath(atPath);
        return Collections.singletonList(result);
    }

    private ConfigValue createConfigValue(Object o) {
        return ConfigValueFactory.fromAnyRef(o, ORIGIN_DESCRIPTION);
    }

    private Optional<Config> fetchEc2MetadataValue(@NonNull String path, Supplier<Object> supplier) {
        try {
            return Optional.ofNullable(supplier.get())
                .map(e -> empty().withValue(path, createConfigValue(e)));
        } catch (Exception e) {
            log.debug("{} error fetching ec2 metadata path {}: {}", this, path, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * {@link ConfigValueFactory#fromAnyRef(Object)} can't handle list or bean values, this method works around this.
     *
     * @param o object to convert to {@link ConfigValue}
     * @return object as {@link ConfigValue}
     */
    @SneakyThrows
    private ConfigValue configValue(Object o) {
        if (o == null) {
            return createConfigValue(null);
        }

        val mapper = Jackson.getObjectMapper();
        val json = mapper.writeValueAsString(o);
        Object res = (o instanceof Collection) ? mapper.readValue(json, List.class) : mapper.readValue(json, Map.class);

        return createConfigValue(res);
    }

    /**
     * Builder for {@link EC2MetadataConfigSource}.
     */
    @Data
    @Accessors(chain = true)
    @EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
    public static class Builder extends ConfigSourceBuilder<Builder> {
        /**
         * {@link Config} path to put AWS EC2 metadata to (default: {@value #DEFAULT_CFG_PATH})
         */
        private String atPath = DEFAULT_CFG_PATH;

        @Override
        public Builder withConfig(@NonNull Config config) {
            configVal(config, "at-path", Config::getString).ifPresent(this::setAtPath);
            return super.withConfig(config);
        }

        @Override
        public ConfigSource build() {
            return new EC2MetadataConfigSource(this);
        }
    }
}
