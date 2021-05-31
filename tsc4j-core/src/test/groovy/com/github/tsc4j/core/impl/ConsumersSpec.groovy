/*
 * Copyright 2017 - 2021 tsc4j project
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
 */

package com.github.tsc4j.core.impl

import groovy.util.logging.Slf4j
import spock.lang.Specification
import spock.lang.Unroll

import java.util.function.Consumer

@Slf4j
@Unroll
class ConsumersSpec extends Specification {
    def "empty() should return consumer"() {
        given:
        def consumer = Consumers.empty()

        expect:
        consumer != null

        when:
        consumer.accept("foo")

        then:
        noExceptionThrown()
    }

    def "empty() should return singleton"() {
        when:
        def consumers = (1..10).collect { Consumers.empty() }
        def first = consumers.first()

        then:
        first instanceof Consumer
        consumers.each { it.is(first) }
    }

    def "safeRun() should behave as expected"() {
        given:
        def wasInvoked = 0
        def consumer = { String it ->
            wasInvoked++
            log.info("string is {} characters long.", it.toLowerCase().length())
        }

        when:
        def result = Consumers.safeRun(consumer, argument)

        then:
        noExceptionThrown()
        result == expected

        where:
        argument | expected
        null     | false
        'foo'    | true
    }
}
