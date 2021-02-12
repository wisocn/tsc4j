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

import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.Environment
import spock.lang.Specification

class Tsc4jPropertySourceLocatorSpec extends Specification {
    def appName = "cuteAppName"
    def configurableEnv = Mock(ConfigurableEnvironment)
    def locator = new Tsc4jPropertySourceLocator(appName, configurableEnv)

    def setupSpec() {
        cleanupSpec()
    }

    def cleanupSpec() {
        SpringUtils.instanceHolder().close()
    }

    def "locate() should return property source"() {
        given:
        def env = Mock(Environment)

        when:
        def source = locator.locate(env)

        then:
        env.getActiveProfiles() >> ["foo", "bar", "foo", "", "  ", "bar"]

        source != null
        source instanceof Tsc4jPropertySource
    }
}
