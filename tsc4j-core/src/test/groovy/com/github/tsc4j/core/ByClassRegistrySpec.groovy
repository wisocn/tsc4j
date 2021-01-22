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

import groovy.util.logging.Slf4j
import spock.lang.Specification
import spock.lang.Unroll

@Slf4j
@Unroll
class ByClassRegistrySpec extends Specification {
    def "empty registry should never return anything"() {
        given:
        def empty = ByClassRegistry.empty()

        expect:
        empty.isEmpty()
        empty.size() == 0
        !empty.get(String).isPresent()
        !empty.get(Object).isPresent()
    }

    def "non-empty registry should behave as expected"() {
        when:
        def registry = ByClassRegistry.empty()
                                      .add(Integer, "foo")
                                      .add(Number, "baz")
                                      .add(Object, "bar")

        then:
        !registry.isEmpty()
        registry.size() == 3

        // we have specific version for integers
        registry.get(Integer).get() == "foo"

        // Long is actually a Number
        registry.get(Long).get() == "baz"

        // all of specified instances inherit Object :-)
        registry.get(Object).get() == "bar"
        registry.get(String).get() == "bar"

        // primitives don't have Object as super type
        !registry.get(int.class).isPresent()

        when: "remove one key"
        registry = registry.remove(Number)

        then:
        !registry.isEmpty()
        registry.size() == 2

        // all of specified instances inherit Object :-)
        registry.get(Object).get() == "bar"
        registry.get(String).get() == "bar"
        registry.get(Long).get() == "bar"

        // we have defined exception for integer
        registry.get(Integer).get() == "foo"

        !registry.get(int.class).isPresent()
    }
}
