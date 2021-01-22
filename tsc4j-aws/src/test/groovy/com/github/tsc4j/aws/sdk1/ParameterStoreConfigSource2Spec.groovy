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

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement
import com.amazonaws.services.simplesystemsmanagement.model.DeleteParametersRequest
import com.amazonaws.services.simplesystemsmanagement.model.ParameterType
import com.amazonaws.services.simplesystemsmanagement.model.PutParameterRequest
import com.github.tsc4j.core.Tsc4jImplUtils
import com.github.tsc4j.testsupport.TestConstants
import com.typesafe.config.ConfigRenderOptions
import groovy.util.logging.Slf4j
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@Ignore("This is meant to be testcontainers based test one day. Sadly AWS SSM is not supported")
@Slf4j
@Unroll
//@Testcontainers
class ParameterStoreConfigSource2Spec extends Specification {
//    @Shared
//    LocalStackContainer localstack = new LocalStackContainer("0.8.9")
//            .withServices(SQS)
//            .withS

    static def parameters = [
        "/a/x"  : [ParameterType.String, "x"],
        "/a/y"  : [ParameterType.StringList, "a,b,c", ["a", "b", "c"]],
        "/a/z"  : [ParameterType.SecureString, "val_a.z"],
        "/b/c/d": [ParameterType.String, "42"]
    ]
    @Shared
    String atPath = "foo.bar"

    def builder = ParameterStoreConfigSource.builder()
                                            .setEndpoint(AwsTestEnv.awsEndpoint)
                                            .setRegion("us-east-1")
    //@Shared
//    @AutoCleanup
//    def source = builder.withPath("/a")
//                        .withPath('/${env}')
//                        .withPath('/${env}/${service}')
//                        .setAtPath(atPath)
//                        .build()

    def setupSpec() {
        TestEnv.apply()
    }

    def setup() {
        ssmSetup()
    }

    def ssmSetup() {
        def source = builder.withPath("/").build()
        def facade = source.ssm
        ssmCleanupTestParameters(facade)
        ssmCreateTestParameters(facade)
    }

    def ssmCleanupTestParameters(SsmFacade facade) {
        AWSSimpleSystemsManagement ssm = facade.ssm
        def names = facade.list().collect { it.getName() }
        Tsc4jImplUtils.partitionList(names, 10).collect {
            def dreq = new DeleteParametersRequest().withNames(it)
            ssm.deleteParameters(dreq)
            log.debug("deleted {} parameters: {}", it.size(), it)
        }
    }

    def ssmCreateTestParameters(SsmFacade facade) {
        AWSSimpleSystemsManagement ssm = facade.ssm

        // create params
        parameters.each {
            def name = it.key
            def e = it.value
            def req = new PutParameterRequest()
                .withType(e[0])
                .withValue(e[1])
                .withName(name)
                .withDescription("desc_" + name)
                .withOverwrite(true)
            ssm.putParameter(req)
            log.debug("created parameter: {} -> {}", name, req)
        }
    }

    def "creation should throw without paths"() {
        when:
        def source = ParameterStoreConfigSource.builder().build()

        then:
        thrown(IllegalStateException)
        source == null
    }

    def "should fetch all all parameters from SSM"() {
        given:
        def source = builder.build()
        def renderOpts = ConfigRenderOptions.defaults().setComments(true)

        when:
        log.info("asking for config using: {}", TestConstants.defaultConfigQuery)
        def config = source.get(TestConstants.defaultConfigQuery)
        log.info("retrieved config: {}", config.root().render(renderOpts))

        then:
        !config.isEmpty()
        config.withoutPath("foo").isEmpty()

        when:
        def cfg = config.getConfig(atPath)

        then:
        cfg.entrySet().size() == parameters.size()
        cfg.getInt("b.c.d") == 42

        parameters.each {
            def name = it.key
            def path = name.replaceFirst('^/+', '').replace('/', '.')
            def expectedValue = it.value.size() > 2 ? it.value[2] : it.value[1]

            assert cfg.hasPath(path)
            assert cfg.getValue(path).unwrapped() == expectedValue
        }

        cleanup:
        source?.close()
    }
}
