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

@Slf4j
@Unroll
class SvcLoaderSpec extends Specification {
    def "should throw NPE for null argument"() {
        when:
        def result = action()

        then:
        thrown(NullPointerException)
        result == null

        where:
        action << [
            { SvcLoader.first(null) },
            { SvcLoader.unorderedStream(null) },
            { SvcLoader.get(null) },
        ]
    }

    def "should return empty instance"() {
        when:
        def result = action()

        then:
        result.isEmpty()

        where:
        action << [
            { SvcLoader.first(String) },
            // { SvcLoader.stream(String) }, // stream doesn't have isEmpty()
            { SvcLoader.get(String) },
        ]
    }

    def "first() should return expected result"() {
        when:
        def instances = (1..10).collect { SvcLoader.first(MyInterface).get() }
        def last = instances.pop()

        then:
        instances.every { it instanceof BarImpl } // due to class-name sorting
        instances.every { !it.is(last) } // returned instances should not be singletons
    }

    def "stream() should return expected result"() {
        when:
        def instances = SvcLoader.unorderedStream(MyInterface).collect(Collectors.toList())

        then:
        instances.size() == 2
        instances.every { it instanceof MyInterface }
        instances[0] instanceof BarImpl // because of alphanum classname  sorting
        instances[1] instanceof FooImpl // because of alphanum classname sorting
    }

    def "get() should return two implementations with correct order"() {
        when:
        def instances = SvcLoader.get(MyInterface)

        then:
        instances.size() == 2
        instances.every { it instanceof MyInterface }
        instances[0] instanceof BarImpl // because of alphanum classname sorting
        instances[1] instanceof FooImpl // because of alphanum classname sorting
    }

    def "get() should return NOT return singleton service instances"() {
        when:
        def results = (1..10).collect { SvcLoader.get(MyInterface) }
        def last = results.pop()

        then:
        last != null
        last.size() == 2
        last.every { it instanceof MyInterface }

        results.every { last != it }
        results.every { !last[0].is(it[0]) && !last[1].is(it[1]) }
    }
}

interface MyInterface {
    String hello()
}

class FooImpl implements MyInterface {
    @Override
    String hello() {
        "foo"
    }
}

class BadImpl implements MyInterface {
    static {
        if (1 == 1) {
            throw new RuntimeException("I refuse to be instantiated")
        }
    }

    @Override
    String hello() {
        "i'm baaad"
    }
}

class BarImpl implements MyInterface {
    @Override
    String hello() {
        "bar"
    }
}
