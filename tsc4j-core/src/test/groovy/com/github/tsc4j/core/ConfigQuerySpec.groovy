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

import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class ConfigQuerySpec extends Specification {
    def defaultAppName = "myApp"
    def defaultDatacenter = "my-datacenter"
    def defaultEnv = "myenv"

    def "should sanitize environments"() {
        given:
        def envs = ["a  ", "b", " a ", "b"]

        when:
        def builder = ConfigQuery.builder()
                                 .appName(defaultAppName)
                                 .datacenter(defaultDatacenter)
                                 .env("")
                                 .env("  ")
                                 .env(null)

        envs.each { builder.env(it) }

        def query = builder.build()

        then:
        query.getAppName() == defaultAppName
        query.getDatacenter() == defaultDatacenter
        query.getEnvs() == ["a", "b"]

        when: "try to modify envs"
        query.getEnvs().add("foo")

        then: "list should be immutable"
        thrown(UnsupportedOperationException)
    }

    def "should throw in case of bad app name: '#appName'"() {
        when:
        def query = defaultBuilder().appName(appName).build()

        then:
        thrown(RuntimeException)
        query == null

        where:
        appName << [null, "", "  ", "a.", "a/", "a^"]
    }

    def defaultBuilder() {
        ConfigQuery.builder()
                   .appName(defaultAppName)
                   .datacenter(defaultDatacenter)
                   .env(defaultEnv)
    }
}
