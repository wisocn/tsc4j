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

package com.github.tsc4j.core

import com.github.tsc4j.testsupport.TestUtils
import com.typesafe.config.ConfigFactory
import groovy.util.logging.Slf4j
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

@Slf4j
@Unroll
@RestoreSystemProperties
class ReloadableConfigFactorySpec extends Specification {
    static def appName = "someApp"
    static def datacenter = "myDatacenter"
    static def envs = ["cloud", "ec2", "dev", "ec2", "zoo", "cloud", "local"]

    def setup() {
        cleanEnv()
    }

    def cleanupSpec() {
        cleanEnv()
    }

    def cleanEnv() {
        TestUtils.clearTsc4jProps()
        TestUtils.clearTsc4jEnvVars()
        ConfigFactory.invalidateCaches()
    }

    def "should create reloadable config"() {
        given:

        when:
        def ts = System.currentTimeMillis()
        log.info("creating rc")
        def rc = ReloadableConfigFactory.defaults()
                                        .setAppName(appName)
                                        .setDatacenter(datacenter)
                                        .setEnvs(envs)
                                        .create()
        def duration = System.currentTimeMillis() - ts
        log.info("created ({} ms): {}", duration, rc)
        ts = System.currentTimeMillis()

        rc.getSync()
        log.info("config fetched after: {}ms", System.currentTimeMillis() - ts)

        then:
        rc != null

        cleanup:
        rc?.close()
    }

    def "should create expected reloadable config from custom config"() {
        given:

        when:
        def ts = System.currentTimeMillis()
        log.info("creating rc")
        def rc = ReloadableConfigFactory.defaults()
                                        .setConfigFile("tsc4j-with-transformer.conf")
                                        .setAppName(appName)
                                        .setDatacenter(datacenter)
                                        .setEnvs(envs)
                                        .create()
        def duration = System.currentTimeMillis() - ts
        log.info("created ({} ms): {}", duration, rc)
        ts = System.currentTimeMillis()

        rc.getSync()
        log.info("config fetched after: {}ms", System.currentTimeMillis() - ts)

        then:
        rc != null

        cleanup:
        rc?.close()

        where:
        i << (1..3)
    }
}
