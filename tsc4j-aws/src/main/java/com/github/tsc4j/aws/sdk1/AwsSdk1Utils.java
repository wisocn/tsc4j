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

import com.amazonaws.ClientConfiguration;
import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.AnonymousAWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.regions.AwsRegionProvider;
import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.github.tsc4j.aws.common.AwsConfig;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Optional;
import java.util.function.Supplier;

import static com.github.tsc4j.core.Tsc4jImplUtils.validString;

/**
 * AWS SDK 1.x utilities.
 */
@Slf4j
@UtilityClass
public class AwsSdk1Utils {
    private static final AwsRegionProvider DEFAULT_REGION_PROVIDER = new DefaultAwsRegionProviderChain();
    private static final AWSCredentialsProvider ANONYMOUS_CRED_PROVIDER =
        new AWSStaticCredentialsProvider(new AnonymousAWSCredentials());

    /**
     * Returns AWS credentials provider from given info.
     *
     * @param config aws info
     * @return credential provider.
     */
    public AWSCredentialsProvider getCredentialsProvider(@NonNull AwsConfig config) {
        if (config.isAnonymousAuth()) {
            return ANONYMOUS_CRED_PROVIDER;
        }

        // static credentials?
        val accessKey = validString(config.getAccessKeyId());
        val secretKey = validString(config.getSecretAccessKey());
        if (accessKey.isEmpty() || secretKey.isEmpty()) {
            return DefaultAWSCredentialsProviderChain.getInstance();
        }

        // fall back to normal credential provider
        return new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey));
    }

    /**
     * Returns aws region provider from given aws config.
     *
     * @param config aws config
     * @return region provider.
     */
    public AwsRegionProvider getRegionProvider(@NonNull AwsConfig config) {
        val region = validString(config.getRegion());
        if (region.isEmpty()) {
            return DEFAULT_REGION_PROVIDER;
        }

        return new AwsRegionProvider() {
            @Override
            public String getRegion() throws SdkClientException {
                return region;
            }
        };
    }

    /**
     * Creates AWS endpoint configuration.
     *
     * @param config aws info
     * @return optional of endpoint configuration.
     * @throws IllegalArgumentException if endpoint is set, but region is not.
     */
    public Optional<EndpointConfiguration> getEndpointConfiguration(@NonNull AwsConfig config) {
        val endpoint = validString(config.getEndpoint());
        val region = validString(config.getRegion());

        if (endpoint.isEmpty()) {
            return Optional.empty();
        } else if (region.isEmpty()) {
            throw new IllegalArgumentException("Custom AWS endpoint configuration requires region to be set.");
        }

        return Optional.of(new EndpointConfiguration(endpoint, region));
    }

    /**
     * Returns AWS client configuration from given configuration.
     *
     * @param config client configuration
     * @return AWS client configuration
     */
    public ClientConfiguration getClientConfiguration(@NonNull AwsConfig config) {
        return new ClientConfiguration()
            .withGzip(config.isGzip())
            .withClientExecutionTimeout((int) config.getTimeout().toMillis())
            .withMaxConnections(config.getMaxConnections())
            .withMaxErrorRetry(config.getMaxErrorRetry());
    }

    /**
     * Configures given AWS SDK 1.x client builder.
     *
     * @param builder client builder
     * @param config  aws info
     * @param <T>     builder type
     * @return given client builder.
     * @throws IllegalArgumentException if builder can't be configured due to bad configuration in aws info
     */
    public <T extends AwsClientBuilder> T configureClientBuilder(@NonNull T builder, @NonNull AwsConfig config) {
        // assign client config and credentials provider
        builder
            .withClientConfiguration(getClientConfiguration(config))
            .withCredentials(getCredentialsProvider(config));

        // custom endpoint?; NOTE: you can't set both region and endpoint configuration
        val endpointOpt = getEndpointConfiguration(config);
        if (endpointOpt.isPresent()) {
            builder.withEndpointConfiguration(endpointOpt.get());
        } else {
            val region = validString(config.getRegion());
            if (!region.isEmpty()) {
                builder.withRegion(region);
            }
        }

        log.debug("configured aws client builder: {}", builder);
        return builder;

    }

    /**
     * Creates configured AWS SDK 1.x client.
     *
     * @param builderSupplier client builder supplier
     * @param config          aws client config
     * @param <T>             builder creation type
     * @return configured aws client instance
     * @throws IllegalArgumentException if builder can't be created from given client config.
     */
    public <T> T configuredClient(@NonNull Supplier<AwsClientBuilder<?, T>> builderSupplier,
                                  @NonNull AwsConfig config) {
        val client = configureClientBuilder(builderSupplier.get(), config).build();
        log.debug("created AWS SDK 1.x client: {}", client);
        return client;
    }
}
