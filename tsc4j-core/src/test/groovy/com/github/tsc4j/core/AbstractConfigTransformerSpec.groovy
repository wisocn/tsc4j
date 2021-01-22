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

import com.github.tsc4j.core.AbstractConfigTransformer
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Specification

abstract class AbstractConfigTransformerSpec extends Specification {
    protected Logger log = LoggerFactory.getLogger(getClass())

    Config getConfig() {
        ConfigFactory.parseResources("testcfg/transformer-test.conf")
                     .withFallback(ConfigFactory.systemProperties())
                     .resolve()
    }

    abstract AbstractConfigTransformer getTransformer()

    def "loaded config should be populated, resolved and non-empty"() {
        when:
        def config = getConfig()

        then:
        config != null
        !config.isEmpty()
        config.isResolved()
    }

    protected boolean isEqualityTestEnabled() {
        true
    }

    def "should return equal configuration"() {
        if (!isEqualityTestEnabled()) {
            return
        }

        when:
        def config = getConfig()
        def res = getTransformer().transform(config)

        then:
        !res.is(config)
        res == config
    }

    def "transformList() should return equal value"() {
        given:
        def list = ["a", "b", "c"]
        def configList = ConfigValueFactory.fromAnyRef(list)

        def transformer = getTransformer()

        when:
        def transformedList = transformer.transformList("foo", configList, null)

        then:
        !transformedList.is(configList)
        transformedList == configList

        !transformedList.unwrapped().is(configList.unwrapped())
        transformedList.unwrapped() == configList.unwrapped()
    }

    def "transformObject() should return equal value"() {
        given:
        def map = ["a": "b", "c": 1]
        def configObject = ConfigValueFactory.fromAnyRef(map)

        def transformer = getTransformer()

        when:
        def transformedObject = transformer.transformObject("foo", configObject, null)

        then:
        !transformedObject.is(configObject)
        transformedObject == configObject

        !transformedObject.unwrapped().is(configObject.unwrapped())
        transformedObject.unwrapped() == configObject.unwrapped()
    }
}
