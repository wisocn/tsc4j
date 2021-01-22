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

package com.github.tsc4j.spring

import org.springframework.core.env.Environment
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class SpringUtilsSpec extends Specification {

    def "getTsc4jEnvs() should return active envs"() {
        given:
        def env = Mock(Environment)

        when:
        def envs = SpringUtils.getTsc4jEnvs(env)

        then:
        env.getActiveProfiles() >> ["x", "y", null, " ", "", "x"]
        env.getDefaultProfiles() >> ["foo", "bar", "bar"]

        envs == ["x", "y"]
    }

    def "getTsc4jEnvs() should return default envs if active are not set"() {
        given:
        def env = Mock(Environment)

        when:
        def envs = SpringUtils.getTsc4jEnvs(env)

        then:
        env.getActiveProfiles() >> []
        env.getDefaultProfiles() >> ["foo", "bar", null, "", " ", "bar"]

        envs == ["foo", "bar"]
    }

    def "getTsc4jEnvs() should throw if active/default are not set"() {
        given:
        def env = Mock(Environment)

        when:
        def envs = SpringUtils.getTsc4jEnvs(env)

        then:
        env.getActiveProfiles() >> []
        env.getDefaultProfiles() >> []

        thrown(IllegalStateException)
    }
}
