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

import com.typesafe.config.ConfigFactory
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

@Unroll
class AwsConfigSpec extends Specification {
    def endpoint = 'http://localhost:9090/'
    def accessKeyId = 'foo'
    def secretAccessKey = 'bar'
    def region = 'us-west-8'
    def timeout = Duration.ofSeconds(42)
    def maxConns = 91
    def maxErrRetry = 98

    def "created instance should contain expected values"() {
        when:
        def awsConfig = new AwsConfig()

        then:
        with(awsConfig) {
            isAnonymousAuth() == false
            getAccessKeyId() == null
            getSecretAccessKey() == null
            getRegion() == null
            getEndpoint() == null
            isGzip() == true
            getTimeout() == Duration.ofSeconds(10)
            getMaxConnections() == 100
            getMaxErrorRetry() == 0
            getS3PathStyleAccess() == null
        }
    }

    def "configuration with config should update the instance state"() {
        given:
        def cfg = ConfigFactory.parseString("""
                anonymous-auth: true
                access-key-id: $accessKeyId
                secret-access-key: $secretAccessKey
                region: $region
                timeout: 42s
                max-connections: $maxConns
                maxErrorRetry: $maxErrRetry
                s3PathStyleAccess: true
            """)

        def awsConfig = new AwsConfig()

        when:
        awsConfig.withConfig(cfg)

        then:
        with(awsConfig) {
            isAnonymousAuth() == true
            getAccessKeyId() == accessKeyId
            getSecretAccessKey() == secretAccessKey
            getRegion() == region
            getEndpoint() == endpoint
            isGzip() == true
            getTimeout() == timeout
            getMaxConnections() == maxConns
            getMaxErrorRetry() == maxErrRetry
            getS3PathStyleAccess() == true
        }
    }
}
