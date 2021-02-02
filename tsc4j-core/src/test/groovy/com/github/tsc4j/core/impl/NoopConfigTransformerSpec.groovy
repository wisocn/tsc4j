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

import com.typesafe.config.ConfigValueFactory
import spock.lang.Specification

class NoopConfigTransformerSpec extends Specification {
    def "instance() should return singleton instance"() {
        when:
        def transformers = (1..10).collect({ NoopConfigTransformer.instance() })
        def first = transformers[0]

        then:
        transformers.each { assert it.is(first) }
    }

    def "should not allow errors and have a stable name"() {
        when:
        def transformer = NoopConfigTransformer.instance()

        then:
        !transformer.allowErrors()
        transformer.getType() == "noop"
        transformer.getName() == ""
    }

    def "transform() should return argument"() {
        given:
        def config = ConfigValueFactory.fromMap([a: "b"]).toConfig()

        when:
        def transformedConfig = NoopConfigTransformer.instance().transform(config)

        then:
        transformedConfig.is(config)
    }
}
