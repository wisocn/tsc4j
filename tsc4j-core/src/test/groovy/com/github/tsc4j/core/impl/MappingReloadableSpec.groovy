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

import com.github.tsc4j.test.TestReloadable
import groovy.util.logging.Slf4j
import spock.lang.Specification
import spock.lang.Unroll

@Slf4j
@Unroll
class MappingReloadableSpec extends Specification {
    def source = new TestReloadable<String>()
    def numMapperInvocations = 0
    def mappingFunction = { String it ->
        numMapperInvocations++
        log.info("mapper invoked with: {}", it);

        it.length()
    };

    def "map() should throw NPE in case of null arguments"() {
        when:
        def r = source.map(null)

        then:
        thrown(NullPointerException)
        r == null

        !source.isPresent()
        source.isEmpty()
        !source.isClosed()
    }

    def "map() on empty reloadable should create empty mapped reloadable"() {
        expect:
        !source.isPresent()
        source.isEmpty()

        when:
        def reloadable = source.map(mappingFunction)
        log.info("map() created: {}", reloadable);

        then:
        reloadable != null
        !reloadable.is(source)
        reloadable != source

        !reloadable.isPresent()
        reloadable.isEmpty()

        mappingFunction
    }

    def "map() on non-empty reloadable should create non-empty mapped reloadable"() {
        given:
        def myValue = Math.random() + '-foo-bar';

        def numMapperInvocations = 0
        def mapper = { String it ->
            numMapperInvocations++
            log.info("mapper invoked with: {}", it);
            it.length()
        }

        and: "assign reloadable value"
        source.setValue(myValue)
        numMapperInvocations == 0

        expect:
        source.isPresent()
        !source.isEmpty()

        when:
        def reloadable = source.map(mapper)
        log.info("map() created: {}", reloadable);

        then:
        reloadable != null
        !reloadable.is(source)
        reloadable != source

        numMapperInvocations == 1

        reloadable.isPresent()
        !reloadable.isEmpty()

        // reloadable should contain expected result
        reloadable.get() == myValue.length()
    }

    def "closing empty mapping reloadable should close upstream as well"() {
        given:
        def reloadable = source.map(mappingFunction)

        expect:
        !source.isClosed()
        !reloadable.isClosed()

        when:
        reloadable.close()

        then:
        source.isClosed()
        reloadable.isClosed() == source.isClosed()
    }

    def "closing non-empty mapping reloadable should close upstream as well"() {
        given:
        def value = 'foo'
        source.set(value)

        def reloadable = source.map(mappingFunction)

        expect:
        !source.isClosed()
        !reloadable.isClosed()
        reloadable.get() == 3

        when:
        reloadable.close()

        then:
        source.isClosed()
        reloadable.isClosed()
        reloadable.isClosed() == source.isClosed()

        reloadable.onClear == null
        reloadable.onClose == null
    }

    def "setting value on a empty source reloadable should work as expected"() {
        given:
        def srcValueA = 'blah'
        def srcValueB = 'foo'

        def numInvocationsA = 0
        def numInvocationsB = 0
        def numInvocationsC = 0
        def numInvocationsOnClear = 0

        and: "setup mapping reloadable"
        def reloadable = source
            .map({ numInvocationsA++; it.toUpperCase() })
            .map({ numInvocationsB++; it.toLowerCase() })
            .map({ numInvocationsC++; it.sha256() })
            .onClear({ numInvocationsOnClear++ })
        log.info("created reloadable: {}", reloadable)

        expect:
        source.isEmpty()
        reloadable.isEmpty()

        when: "set value to a reloadable"
        source.set(srcValueA)
        log.info("source: {}, mapped: {}", source, reloadable)

        then:
        source.isPresent()

        numInvocationsA == 1
        numInvocationsB == 1
        numInvocationsC == 1
        numInvocationsOnClear == 0

        reloadable.isPresent()
        !reloadable.isEmpty()
        reloadable.get() == srcValueA.toLowerCase().sha256()

        when: "remove value from source reloadable many times"
        10.times { source.removeValue() }

        then:
        source.isEmpty()
        reloadable.isEmpty()

        numInvocationsA == 1
        numInvocationsB == 1
        numInvocationsC == 1
        numInvocationsOnClear == 1

        when: "assign value to source reloadable many times"
        10.times { source.setValue(srcValueB) }

        then:
        source.isPresent()

        reloadable.isPresent()
        reloadable.get() == srcValueB.toLowerCase().sha256()

        numInvocationsA == 2
        numInvocationsB == 2
        numInvocationsC == 2
        numInvocationsOnClear == 1
    }

    def "setting value on a non-empty source reloadable should work as expected"() {
        given:
        def srcValueA = 'blah'
        def srcValueB = 'foo'

        def numInvocationsA = 0
        def numInvocationsB = 0
        def numInvocationsC = 0
        def numInvocationsOnClear = 0

        and: "setup mapping reloadable"
        def reloadable = source
            .set(srcValueA)
            .map({ numInvocationsA++; it.toUpperCase() })
            .map({ numInvocationsB++; it.toLowerCase() })
            .map({ numInvocationsC++; it.sha256() })
            .onClear({ numInvocationsOnClear++ })
        log.info("created reloadable: {}", reloadable)

        expect:
        source.isPresent()

        numInvocationsA == 1
        numInvocationsB == 1
        numInvocationsC == 1
        numInvocationsOnClear == 0

        reloadable.isPresent()
        !reloadable.isEmpty()
        reloadable.get() == srcValueA.toLowerCase().sha256()

        when: "remove value from source reloadable many times"
        10.times { source.removeValue() }

        then:
        source.isEmpty()
        reloadable.isEmpty()

        numInvocationsA == 1
        numInvocationsB == 1
        numInvocationsC == 1
        numInvocationsOnClear == 1

        when: "assign value to source reloadable many times"
        10.times { source.setValue(srcValueB) }

        then:
        source.isPresent()

        reloadable.isPresent()
        reloadable.get() == srcValueB.toLowerCase().sha256()

        numInvocationsA == 2
        numInvocationsB == 2
        numInvocationsC == 2
        numInvocationsOnClear == 1
    }

    def "mapper function throws: empty source reloadable on update "() {
        given:
        def srcValueA = 'blah'

        def numInvocationsA = 0
        def numInvocationsB = 0

        and:
        def reloadable = source
            .map({ numInvocationsA++; it.toUpperCase() })
            .map({ numInvocationsB++; throw new RuntimeException("not today") })

        expect:
        source.isEmpty()
        reloadable.isEmpty()

        when:
        source.set(srcValueA)
        log.info("reloadable: {}", reloadable)

        then:
        noExceptionThrown()

        source.isPresent()
        reloadable.isEmpty()

        numInvocationsA == 1
        numInvocationsB == 1
    }

    def "mapper function throws: non-empty source reloadable on update "() {
        given:
        def srcValueA = 'blah'
        def exception = new RuntimeException("not today")

        def numInvocationsA = 0
        def numInvocationsB = 0

        when:
        def reloadable = source
            .set(srcValueA)
            .map({ numInvocationsA++; it.toUpperCase() })
            .map({ numInvocationsB++; throw exception })

        then:
        def ex = thrown(RuntimeException)
        ex.is(exception)

        source.isPresent()
        reloadable == null
    }
}
