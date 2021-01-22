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

import com.github.tsc4j.core.Tsc4j
import com.github.tsc4j.core.Tsc4jImplUtils
import com.github.tsc4j.testsupport.TestConstants
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import groovy.util.logging.Slf4j
import spock.lang.Specification
import spock.lang.Timeout
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

import static com.amazonaws.SDKGlobalConfiguration.EC2_METADATA_SERVICE_OVERRIDE_SYSTEM_PROPERTY
import static com.github.tsc4j.aws.sdk1.EC2MetadataConfigSource.DEFAULT_CFG_PATH

@Slf4j
@Unroll
class EC2MetadataConfigSourceSpec extends Specification {
    def "should create valid e2 metadata config source builder"() {
        when:
        def config = ConfigValueFactory.fromMap([impl: impl]).toConfig()
        def source = Tsc4jImplUtils.createConfigSource(config, 1).get()

        then:
        source != null
        source instanceof EC2MetadataConfigSource

        where:
        impl << [
            "aws.ec2.metadata",
            " Aws.ec2.metadatA ",
            "ec2.metadaTA",
            "ec2",
            " EC2 ",
            "EC2MetadataConfigSource",
            "com.github.tsc4j.aws.sdk1.EC2MetadataConfigSource"
        ]
    }

    @Timeout(1)
    @RestoreSystemProperties
    def "should return expected values with installed at: '#atPath'"() {
        given:
        def cfgMap = ['impl': "ec2", 'at-path': atPath]
        def sourceConfig = ConfigFactory.parseMap(cfgMap)
        def source = Tsc4jImplUtils.createConfigSource(sourceConfig, 1).get()

        def expectedPath = Tsc4j.configPath(atPath)

        and: "set aws ec2 system properties"
        def listenPort = 9090
        // used ec2-mock docker image doesn't allow specifying listening port, so on circleci we need to use port 80
        // https://github.com/bpholt/fake-ec2-metadata-service/blob/master/ec2-metadata-service.rb#L12
        if (System.getenv('CIRCLECI') != null) {
            listenPort = 80
        }
        System.setProperty(EC2_METADATA_SERVICE_OVERRIDE_SYSTEM_PROPERTY, "http://localhost:${listenPort}")

        when:
        def config = source.get(TestConstants.defaultConfigQuery);
        log.info("fetched config: {}", Tsc4j.render(config, 3))
        log.info("expected path: '{}'", expectedPath)

        then:
        !config.isEmpty()

        when: "fetch real config"
        def ec2Config = expectedPath.isEmpty() ? config.getConfig(DEFAULT_CFG_PATH) : config.getConfig(expectedPath)
        log.info("ec2 config: {}", Tsc4j.render(ec2Config, 3))

        then:
        !ec2Config.isEmpty()
        !ec2Config.getString('ami-id').isEmpty()
        !ec2Config.getString('instance-id').isEmpty()
        !ec2Config.getString('private-ip-address').isEmpty()

        where:
        atPath << [null, "", ".", "foo"]
    }
}
