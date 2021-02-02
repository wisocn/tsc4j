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

import com.github.tsc4j.core.AbstractConfigSource
import com.github.tsc4j.core.ConfigSourceBuilder
import com.github.tsc4j.testsupport.BaseSpec
import com.github.tsc4j.testsupport.TestConstants
import com.typesafe.config.ConfigException
import groovy.util.logging.Slf4j
import spock.lang.Unroll

import java.nio.charset.StandardCharsets

import static com.github.tsc4j.testsupport.TestConstants.TEST_CFG_INVALID_STR
import static com.github.tsc4j.testsupport.TestConstants.TEST_CFG_STRING

@Slf4j
abstract class AbstractConfigSourceSpec extends BaseSpec {
    protected static def defaultAppName = TestConstants.defaultAppName
    protected static def defaultConfigQuery = TestConstants.defaultConfigQuery
    protected static def defaultDatacenter = TestConstants.defaultDatacenter
    protected static def defaultEnvs = TestConstants.defaultEnvs

    /**
     * Returns dummy config source used for top-level tests.
     * @return config source
     */
    abstract AbstractConfigSource dummySource(String appName)

    /**
     * Creates builder
     * @return builder
     */
    abstract ConfigSourceBuilder dummyBuilder()

    AbstractConfigSource dummySource() {
        dummySource(defaultAppName)
    }

    @Unroll
    def "sanitizePaths() should return correct result"() {
        when:
        def res = dummySource().sanitizePaths(paths)

        then:
        res == expected

        where:
        paths                                                   | expected
        ["", " ", null]                                         | []
        ["", " ", null, "a", "a", " //a/// ", " //a", " ///a "] | ["a", "/a/", "/a"]
    }

    @Unroll
    def "readConfig() should correctly parse input"() {
        given:
        def origin = "someOriginName"

        when:
        def config = dummySource().readConfig(input, origin)
        log.info("parsed: {}", config)

        then:
        !config.isEmpty()
        config.getInt('a') == 42
        config.getBoolean('b') == true

        where:
        input << [
            TEST_CFG_STRING.getBytes(StandardCharsets.UTF_8),
            TEST_CFG_STRING,
            new ByteArrayInputStream(TEST_CFG_STRING.getBytes(StandardCharsets.UTF_8)),
            new InputStreamReader(new ByteArrayInputStream(TEST_CFG_STRING.getBytes(StandardCharsets.UTF_8)))
        ]
    }

    @Unroll
    def "readConfig() should correctly handle parse errors"() {
        given:
        def origin = "someOriginName"

        when:
        def config = dummySource().readConfig(input, origin)

        then:
        def exception = thrown(ConfigException)
        config == null

        exception.origin().description().contains(origin)
        exception.origin().lineNumber() == 7
        exception.getMessage().contains(': 7: expecting a close parentheses ')

        where:
        input << [
            TEST_CFG_INVALID_STR.getBytes(StandardCharsets.UTF_8),
            TEST_CFG_INVALID_STR,
            new ByteArrayInputStream(TEST_CFG_INVALID_STR.getBytes(StandardCharsets.UTF_8)),
            new InputStreamReader(new ByteArrayInputStream(TEST_CFG_INVALID_STR.getBytes(StandardCharsets.UTF_8)))
        ]
    }

    @Unroll
    def "basename('#input') should return '#expected'"() {
        where:
        input           | expected
        '/path/to'      | 'to'
        '/path/to/'     | 'to'
        '/path/to/.'    | '.'
        '/path/to/./'   | '.'
        '/path/to/.///' | '.'
        '/path/to//'    | 'to'
        '/path/to/ /'   | ' '
        '/path/to/ //'  | ' '
        ' '             | ' '
        ''              | ''
    }
}
