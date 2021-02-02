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

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class AggConfigTransformerSpec extends Specification {

    def "should throw on null args"() {
        when: "instantiate with null list"
        new AggConfigTransformer((List) null)

        then:
        thrown(NullPointerException)

        when: "instantiate with null array"
        new AggConfigTransformer((ConfigTransformer[]) null)

        then:
        thrown(NullPointerException)
    }

    def "instance with only null elements should result in empty transformer list and transformation should return the same config"() {
        given:
        def config = ConfigValueFactory.fromMap([a: UUID.randomUUID().toString()]).toConfig()

        expect:
        transformer.allowErrors() == false
        transformer.getName() == "agg"
        transformer.getTransformers().isEmpty()

        when: "ask for transformation"
        def transformedConfig = transformer.transform(config)

        then:
        transformedConfig == config
        transformedConfig.is(config)
        transformedConfig.getString("a") == config.getString("a")

        where:
        transformer << [
            new AggConfigTransformer(null, null, null),
            new AggConfigTransformer([null, null, null]),
        ]
    }

    def "constructor should weed out null transformers and maintain collection/array order"() {
        given:
        def transformerA = Mock(ConfigTransformer)
        def transformerB = Mock(ConfigTransformer)

        when:
        def transformer = closure.call(transformerB, transformerA)

        then:
        noExceptionThrown()
        transformer != null

        transformer.getTransformers() == [transformerB, transformerA]

        where:
        closure << [
            { a, b -> new AggConfigTransformer(a, null, b, null, null) },
            { a, b -> new AggConfigTransformer([a, null, b, null, null]) },
        ]
    }

    def "transform() should throw if one transformer throws"() {
        given:
        def transformerA = Mock(ConfigTransformer)
        def transformerB = Mock(ConfigTransformer)
        def config = ConfigFactory.empty()

        def exception = new RuntimeException("foo")
        def transformer = new AggConfigTransformer(transformerA, transformerB)

        when:
        def transformedConfig = transformer.transform(config)

        then:
        1 * transformerA.transform(config) >> { throw exception } // first transformer throws
        0 * transformerB._ // second transformer should not be invoked

        def thrown = thrown(RuntimeException)
        thrown.getCause().is(exception)

        transformedConfig == null
    }

    def "transform() should not throw if one transformer throws and it allows errors"() {
        given:
        def transformerA = Mock(ConfigTransformer)
        def transformerB = Mock(ConfigTransformer)
        def config = ConfigFactory.empty()

        def exception = new RuntimeException("foo")
        def transformer = new AggConfigTransformer(transformerA, transformerB)

        when:
        def transformedConfig = transformer.transform(config)

        then:
        1 * transformerA.transform(config) >> { throw exception } // first transformer throws
        1 * transformerA.allowErrors() >> true
        1 * transformerB.transform(config) >> ConfigValueFactory.fromMap([a: "b", c: "d"]).toConfig()

        noExceptionThrown()
        !transformedConfig.isEmpty()
        transformedConfig.getString("a") == "b"
        transformedConfig.getString("c") == "d"
    }

    def "transform() should transform config even if transformers return null or throw"() {
        given:
        def transformerA = Mock(ConfigTransformer)
        def transformerB = Mock(ConfigTransformer)
        def transformerC = Mock(ConfigTransformer)
        def transformerD = Mock(ConfigTransformer)

        and: "setup config"
        def config = ConfigFactory.empty()
        def configA = ConfigValueFactory.fromMap(a: "x", b: "y").toConfig()
        def configB = ConfigValueFactory.fromMap(a: "z").toConfig()
        def configC = null

        and: "setup transformer"
        def transformer = new AggConfigTransformer(transformerD, transformerB, transformerC, transformerA)

        when:
        def transformedConfig = transformer.transform(config)

        then:
        1 * transformerA.transform(_) >> configA
        1 * transformerB.transform(_) >> configB
        1 * transformerC.transform(_) >> configC

        // this transformer fails
        1 * transformerD.transform(_) >> { throw new RuntimeException("i refuse to cooperate") }
        1 * transformerD.allowErrors() >> true

        // verify transformed config contents
        transformedConfig != config
        !transformedConfig.isEmpty()

        transformedConfig.getString("a") == "x"
        transformedConfig.getString("b") == "y"
    }

    def "close() should close all transformers"() {
        given:
        def transformerA = Mock(ConfigTransformer)
        def transformerB = Mock(ConfigTransformer)

        def transformer = new AggConfigTransformer(transformerB, transformerA)

        expect:
        transformer.getTransformers() == [transformerB, transformerA]

        when:
        transformer.close()

        then:
        1 * transformerA.close() >> { throw new RuntimeException("b00m") }
        1 * transformerB.close()

        noExceptionThrown()
    }
}
