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

package com.github.tsc4j.core.impl

import com.github.tsc4j.testsupport.TestConstants
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

class CliConfigSourceSpec extends Specification {
    @RestoreSystemProperties
    def "should correctly parse system property string"() {
        given:
        def cliArgsStr = 'blah blah --foo=bar --bar.baz true -xy 10 --a.b.c=d --e.f.g h --💩 poop'

        System.setProperty(CliConfigSource.PROP_NAME, cliArgsStr)

        and: "setup source"
        def source = new CliConfigSource()

        when:
        def config = source.get(TestConstants.defaultConfigQuery)

        then:
        config.getString("foo") == "bar"
        config.getBoolean("bar.baz") == true
        config.getString("a.b.c") == "d"
        config.getString("e.f.g") == "h"
        config.getString("💩") == "poop"

        config.root().values().every() { it.origin().description() == "cli" }
    }

    def "instance() should return empty config"() {
        when:
        def source = CliConfigSource.instance()

        then:
        source.getName().isEmpty()
        source.getType() == "cli"
        source.get(TestConstants.defaultConfigQuery).isEmpty()
    }
}
