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

package com.github.tsc4j.aws.common

import spock.lang.Specification

import java.time.Duration

class WithAwsConfigSpec extends Specification {
    def awsConfig = new AwsConfig()

    def endpoint = 'http://localhost:9090/'
    def accessKeyId = 'foo'
    def secretAccessKey = 'bar'
    def region = 'us-west-8'
    def timeout = Duration.ofSeconds(42)
    def maxConns = 91
    def maxErrRetry = 98

    def "setting values with setters should affect created aws config"() {
        given:

        def bean = new MyBean(awsConfig)

        when:
        bean
            .setAnonymousAuth(true)
            .setAccessKeyId(accessKeyId)
            .setSecretAccessKey(secretAccessKey)
            .setRegion(region)
            .setEndpoint(endpoint)
            .setGzip(true)
            .setTimeout(timeout)
            .setMaxConnections(maxConns)
            .setMaxErrorRetry(maxErrRetry)
            .setS3PathStyleAccess(true)

        then:
        def config = bean.getAwsConfig()

        then:
        assertAwsConfig(config)
    }

    def assertAwsConfig(AwsConfig cfg) {
        with(cfg) {
            assert isAnonymousAuth() == true
            assert getAccessKeyId() == accessKeyId
            assert getSecretAccessKey() == secretAccessKey
            assert getRegion() == region

            assert isGzip() == true
            assert getTimeout() == timeout
            assert getMaxConnections() == maxConns
            assert getMaxErrorRetry() == maxErrRetry

            assert getS3PathStyleAccess() == true
        }

        true
    }

    class MyBean implements WithAwsConfig {
        private final AwsConfig awsConfig

        MyBean(AwsConfig awsConfig) {
            this.awsConfig = awsConfig
        }

        @Override
        AwsConfig getAwsConfig() {
            awsConfig
        }
    }
}
