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

package com.github.tsc4j.aws.sdk1

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.regions.DefaultAwsRegionProviderChain
import com.github.tsc4j.aws.common.AwsConfig
import com.typesafe.config.ConfigFactory
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

@Unroll
class AwsSdk1UtilsSpec extends Specification {
    def "getCredentialsProvider() should return default credentials provider if both keys are not specified"() {
        given:
        def awsConfig = new AwsConfig().setAccessKeyId(accessKey).setSecretAccessKey(secretKey)

        when:
        def provider = AwsSdk1Utils.getCredentialsProvider(awsConfig)

        then:
        provider != null
        provider instanceof DefaultAWSCredentialsProviderChain

        where:
        accessKey | secretKey
        null      | null
        ""        | null
        null      | ""
        " "       | null
        null      | " "
        " "       | "   "
    }

    def "getCredentialsProvider() should return static credentials provider if both keys are specified"() {
        given:
        def awsConfig = new AwsConfig().setAccessKeyId(accessKey)
                                       .setSecretAccessKey(secretKey)

        when:
        def provider = AwsSdk1Utils.getCredentialsProvider(awsConfig)

        then:
        provider != null
        provider instanceof AWSStaticCredentialsProvider

        when:
        def credentials = provider.getCredentials()

        then:
        credentials instanceof BasicAWSCredentials

        where:
        accessKey | secretKey
        "a"       | " b"
        "a"       | " b"
    }

    def "getRegionProvider() should return default region provider chain for invalid input"() {
        given:
        def awsConfig = new AwsConfig().setRegion(region)

        when:
        def provider = AwsSdk1Utils.getRegionProvider(awsConfig)

        then:
        provider instanceof DefaultAwsRegionProviderChain

        where:
        region << [null, "", " ", "  "]
    }

    def "getRegionProvider() should return supplied region for valid input"() {
        given:
        def awsConfig = new AwsConfig().setRegion(region)

        when:
        def provider = AwsSdk1Utils.getRegionProvider(awsConfig)

        then:
        provider.getRegion() == region.trim()

        where:
        region << ["us-west-1", " foo-region "]
    }

    def "getCredentialsProvider() should return default provider chain if not all props are set"() {
        // don't run test if both keys are set
        if (accessKey?.trim()?.length() > 0 && secretKey?.trim()?.length()) {
            return
        }

        given:
        def awsConfig = new AwsConfig().setAccessKeyId(accessKey)
                                       .setSecretAccessKey(secretKey)

        when:
        def provider = AwsSdk1Utils.getCredentialsProvider(awsConfig)

        then:
        provider instanceof DefaultAWSCredentialsProviderChain

        where:
        [accessKey, secretKey] << [[null, " ", "accessKey"], [null, " ", "secretKey"]].combinations()
    }

    def "getCredentialsProvider() should basic credentials provider if both keys are set"() {
        given:
        def accessKey = "foo"
        def secretKey = "bar"
        def awsConfig = new AwsConfig().setAccessKeyId(accessKey)
                                       .setSecretAccessKey(secretKey)

        when:
        def provider = AwsSdk1Utils.getCredentialsProvider(awsConfig)

        then:
        provider instanceof AWSStaticCredentialsProvider
        provider.getCredentials().getAWSAccessKeyId() == accessKey
        provider.getCredentials().getAWSSecretKey() == secretKey
    }

    def "getRegionProvider() should return default region provider if no region is set"() {
        given:
        def config = new AwsConfig().setRegion(region)
        expect:
        AwsSdk1Utils.getRegionProvider(config) instanceof DefaultAwsRegionProviderChain

        where:
        region << [null, "", "  "]
    }

    def "getRegionProvider() should return static region provider if region is set"() {
        given:
        def config = new AwsConfig().setRegion(region)
        when:
        def provider = AwsSdk1Utils.getRegionProvider(config)

        then:
        provider.getRegion() == region.trim()

        where:
        region << ["us-west-1", "   us-west-2 ", " fooRegion"]
    }

    def "getEndpointConfiguration() should return empty optional when endpoint is not defined or empty"() {
        given:
        def emptyConfig = new AwsConfig()
        def mapConfig = ConfigFactory.parseMap(["endpoint": endpoint])

        expect:
        !AwsSdk1Utils.getEndpointConfiguration(emptyConfig).isPresent()
        !AwsSdk1Utils.getEndpointConfiguration(emptyConfig.setEndpoint(endpoint)).isPresent()
        !AwsSdk1Utils.getEndpointConfiguration({ emptyConfig.withConfig(mapConfig); emptyConfig }.call()).isPresent()

        where:
        endpoint << [null, "", "    "]
    }

    def "getEndpointConfiguration() should return optional with endpoint"() {
        given:
        def cfg = ConfigFactory.parseMap([endpoint: endpoint, region: region])
        def awsConfig = new AwsConfig().setEndpoint(endpoint)
                                       .setRegion(region)

        when:
        def configOpt = AwsSdk1Utils.getEndpointConfiguration(awsConfig)

        then:
        configOpt.isPresent()

        when:
        def config = configOpt.get()

        then:
        config.getServiceEndpoint() == endpoint.trim()
        config.getSigningRegion() == "us-west-3"

        when: "also check that the same is true if object is configured with configuration"
        awsConfig = new AwsConfig()
        awsConfig.withConfig(cfg)
        config = AwsSdk1Utils.getEndpointConfiguration(awsConfig).get()

        then:
        config.getServiceEndpoint() == endpoint.trim()
        config.getSigningRegion() == "us-west-3"

        where:
        [endpoint, region] << [["foo", "  foo  "], [" us-west-3 "]].combinations()
    }

    def "getClientConfiguration() should return expected configuration"() {
        given:
        def endpoint = 'http://localhost:9090/'
        def region = 'us-west-5'
        def timeout = Duration.ofSeconds(42)
        def maxConn = 667
        def maxErrRetry = 2

        def cfgMap = [
            'endpoint'       : endpoint,
            'region'         : region,
            'gzip'           : gzip,
            'timeout'        : timeout,
            'max-connections': maxConn,
            'max-error-retry': maxErrRetry
        ]
        def cfg = ConfigFactory.parseMap(cfgMap)

        when:
        def awsConfig = new AwsConfig()
            .setRegion(region)
            .setEndpoint(endpoint)
            .setGzip(gzip)
            .setTimeout(timeout)
            .setMaxConnections(maxConn)
            .setMaxErrorRetry(maxErrRetry)

        then:
        awsConfig.isGzip() == gzip
        awsConfig.getTimeout() == timeout
        awsConfig.getMaxConnections() == maxConn
        awsConfig.getMaxErrorRetry() == maxErrRetry

        when:
        def endpointConfig = AwsSdk1Utils.getEndpointConfiguration(awsConfig).get()

        then:
        endpointConfig.getServiceEndpoint() == endpoint
        endpointConfig.getSigningRegion() == region

        when: "check whether config is the same if instance is configured with config"
        awsConfig = new AwsConfig()
        awsConfig.withConfig(cfg)

        then:
        with(awsConfig) {
            isGzip() == gzip
            getTimeout() == timeout
            getMaxConnections() == maxConn
            getMaxErrorRetry() == maxErrRetry
        }

        when:
        endpointConfig = AwsSdk1Utils.getEndpointConfiguration(awsConfig).get()

        then:
        endpointConfig.getServiceEndpoint() == endpoint
        endpointConfig.getSigningRegion() == region

        where:
        gzip << [true, false]
    }
}
