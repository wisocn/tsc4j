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

import com.github.tsc4j.core.impl.OnlyOnce
import groovy.util.logging.Slf4j
import spock.lang.Specification

import java.util.function.Consumer

@Slf4j
class OnlyOnceSpec extends Specification {
    def maxItems = 10
    def invokedWith = []
    def consumer = {
        log.debug("invoked with: {}", it)
        invokedWith.add(it)
    } as Consumer<String>
    def set = new OnlyOnce<String>(consumer, maxItems)

    def "should run given consumer only once"() {
        given:
        def item = 'foo'

        when:
        def results = (1..maxItems).collect { set.add(item) }

        then:
        results.pop() == true
        results.each { assert it == false }

        invokedWith.size() == 1
        invokedWith[0] == item
    }

    def "should perform maintenance if over capacity"() {
        given:
        def items = (1..maxItems * 20).collect { "item-$it" }

        when:
        items.each { set.add(it) }

        then:
        set.set.size() == 1
        set.set[0] == 'item-200'
    }
}
