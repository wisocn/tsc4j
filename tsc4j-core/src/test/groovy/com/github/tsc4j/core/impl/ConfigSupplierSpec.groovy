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

import com.github.tsc4j.core.ConfigQuery
import com.github.tsc4j.core.ConfigSource
import com.typesafe.config.ConfigFactory
import groovy.util.logging.Slf4j
import spock.lang.Specification
import spock.lang.Unroll

@Slf4j
@Unroll
class ConfigSupplierSpec extends Specification {
    static def defQuery = ConfigQuery.builder()
                                     .appName("my-app")
                                     .datacenter("some-dc")
                                     .availabilityZone("some-zone")
                                     .envs(["foo", "bar"])
                                     .build()

    def source = Mock(ConfigSource)
    def supplier = new ConfigSupplier(source, defQuery)

    def "constructor should thrown on null args"() {
        if (source != null && defQuery != null) {
            return
        }

        when:
        def supplier = new ConfigSupplier(source, query)

        then:
        thrown(NullPointerException)
        supplier == null

        where:
        [source, query] << [[Mock(ConfigSource), null], [defQuery, null]].combinations()
    }

    def "should call delegate config source"() {
        given:
        def config = ConfigFactory.empty("foo")

        when:
        def result = supplier.get()

        then:
        1 * source.get(defQuery) >> config

        result.is(config)
        result.isEmpty()
    }

    def "should not suppress exception"() {
        given:
        def exception = new RuntimeException("b00m")

        when:
        def result = supplier.get()

        then:
        1 * source.get(defQuery) >> { throw exception }

        def thrown = thrown(RuntimeException)
        thrown.is(exception)
        result == null
    }

    def "closing supplier should close delegate config source"() {
        when:
        supplier.close()

        then:
        1 * source.close()
    }
}
