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

import com.github.tsc4j.testsupport.TestConstants
import com.typesafe.config.ConfigValueFactory
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class ConfigSourceWithTransformerSpec extends Specification {
    def source = Mock(ConfigSource)
    def transformer = Mock(ConfigTransformer)
    def query = TestConstants.defaultConfigQuery

    def sourceConfig = ConfigValueFactory.fromMap([x: "y"]).toConfig()
    def transformedConfig = ConfigValueFactory.fromMap([a: "b"]).toConfig()

    def "constructor should throw on null arguments"() {
        when:
        def s = new ConfigSourceWithTransformer(source, transformer)

        then:
        thrown(NullPointerException)
        s == null

        where:
        source             | transformer
        null               | null
        null               | Mock(ConfigTransformer)
        Mock(ConfigSource) | null
    }

    def "successful config fetch should call transformer"() {
        given:
        def s = new ConfigSourceWithTransformer(source, transformer)

        when:
        def config = s.get(query)

        then:
        1 * source.get(query) >> sourceConfig
        1 * transformer.transform(sourceConfig) >> transformedConfig

        config == transformedConfig
    }

    def "successful config fetch with transformer failure should result in failure"() {
        given:
        def s = new ConfigSourceWithTransformer(source, transformer)
        def exception = new RuntimeException("b00m")

        when:
        def config = s.get(query)

        then:
        1 * source.get(query) >> sourceConfig
        1 * transformer.transform(sourceConfig) >> { throw exception }

        def ex = thrown(Exception)
        ex == exception
        config == null
    }

    def "failed config fetch should not invoke transformer"() {
        given:
        def s = new ConfigSourceWithTransformer(source, transformer)
        def exception = new RuntimeException("b00m")

        when:
        def config = s.get(query)

        then:
        1 * source.get(query) >> { throw exception }
        0 * transformer.transform(_) >> transformedConfig

        def ex = thrown(Exception)
        ex == exception
        config == null
    }

    def "close() should close both source and transformer"() {
        given:
        def s = new ConfigSourceWithTransformer(source, transformer)

        when:
        s.close()

        then:
        1 * source.close()
        1 * transformer.close()
    }

    def "close() should try to close both source and transformer even in case of exceptions"() {
        given:
        def sourceException = new RuntimeException("source")
        def transformerException = new RuntimeException("transformer")
        def s = new ConfigSourceWithTransformer(source, transformer)

        when:
        s.close()

        then:
        1 * source.close() >> { throw sourceException }
        1 * transformer.close() >> { throw transformerException }

        noExceptionThrown() // .close() should not throw exception
    }
}
