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
class FilterReloadableSpec extends Specification {
    def source = new TestReloadable<String>()
    def numPredicateInvocations = 0
    def predicate = { String it ->
        numPredicateInvocations++
        def result = it.length() > 5
        log.info("predicate invoked with: {} => {}", it, result);
        result
    }

    def "closing filter reloadable should close source reloadable"() {
        given:
        def reloadable = source.filter(predicate)

        expect:
        !source.isClosed()
        !reloadable.isClosed()

        when:
        reloadable.close()

        then:
        source.isClosed()
        reloadable.isClosed()

        where:
        source << [new TestReloadable<String>(), new TestReloadable<String>('foo')]
    }

    def "closing source reloadable should close filter reloadable"() {
        given:
        def reloadable = source.filter(predicate)

        expect:
        !source.isClosed()
        !reloadable.isClosed()

        when:
        source.close()

        then:
        source.isClosed()
        reloadable.isClosed()

        where:
        source << [new TestReloadable<String>(), new TestReloadable<String>('foo')]
    }

    def "filter() should throw NPE in case of null arguments"() {
        when:
        def r = source.filter(null)

        then:
        thrown(NullPointerException)
        r == null

        !source.isPresent()
        source.isEmpty()
        !source.isClosed()
    }

    def "filter() on empty reloadable should create empty filtered reloadable"() {
        expect:
        !source.isPresent()
        source.isEmpty()

        when:
        def reloadable = source.filter(predicate)
        log.info("filter() created: {}", reloadable);

        then:
        reloadable != null
        !reloadable.is(source)
        reloadable != source

        !reloadable.isPresent()
        reloadable.isEmpty()
    }

    def "filter() on non-empty reloadable should create empty filtered reloadable"() {
        given:
        def myValue = 'foo';

        and: "assign reloadable value"
        source.setValue(myValue)
        numPredicateInvocations == 0

        expect:
        source.isPresent()

        when: "created filtered reloadable"
        def reloadable = source.filter(predicate)
        log.info("filter() created: {}", reloadable);

        then: "target reloadable should be empty because myValue doesn't satisfy the predicate"
        reloadable != null
        !reloadable.is(source)
        reloadable != source

        reloadable.isEmpty() // `foo` is shorted than 5 chars
        numPredicateInvocations == 1

        when: "set new value"
        def newValue = UUID.randomUUID().toString() + myValue
        source.set(newValue)

        then:
        reloadable.isPresent()
        reloadable.get() == newValue
        numPredicateInvocations == 2
    }

    def "closing empty filtering reloadable should close upstream as well"() {
        given:
        def reloadable = source.filter(predicate)

        expect:
        !source.isClosed()
        !reloadable.isClosed()

        when:
        reloadable.close()

        then:
        source.isClosed()
        reloadable.isClosed() == source.isClosed()
    }

    def "closing non-empty filtering reloadable should close upstream as well"() {
        given:
        def value = 'foobar'
        source.set(value)

        def reloadable = source.filter(predicate)

        expect:
        !source.isClosed()
        !reloadable.isClosed()
        reloadable.isPresent()

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
        def numInvocationsOnClear = 0

        and: "setup filtering reloadable"
        def reloadable = source
            .filter({ numInvocationsA++; it.length() > 2 })
            .filter({ numInvocationsB++; it.length() < 10 })
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
        numInvocationsOnClear == 0

        reloadable.isPresent()
        !reloadable.isEmpty()
        reloadable.get() == srcValueA

        when: "remove value from source reloadable many times"
        10.times { source.removeValue() }

        then:
        source.isEmpty()
        reloadable.isEmpty()

        numInvocationsA == 1
        numInvocationsB == 1
        numInvocationsOnClear == 1

        when: "assign value to source reloadable many times"
        10.times { source.setValue(srcValueB) }

        then:
        source.isPresent()

        reloadable.isPresent()
        reloadable.get() == srcValueB

        numInvocationsA == 2
        numInvocationsB == 2
        numInvocationsOnClear == 1
    }

    def "setting value on a non-empty source reloadable should work as expected"() {
        given:
        def srcValueA = 'blah'
        def srcValueB = 'foo'

        def numInvocationsA = 0
        def numInvocationsB = 0
        def numInvocationsOnClear = 0

        and: "setup filtering reloadable"
        def reloadable = source
            .set(srcValueA)
            .filter({ numInvocationsA++; it.length() > 2 })
            .filter({ numInvocationsB++; it.length() < 10 })
            .onClear({ numInvocationsOnClear++ })
        log.info("created reloadable: {}", reloadable)

        expect:
        source.isPresent()

        numInvocationsA == 1
        numInvocationsB == 1
        numInvocationsOnClear == 0

        reloadable.isPresent()
        !reloadable.isEmpty()
        reloadable.get() == srcValueA

        when: "remove value from source reloadable many times"
        10.times { source.removeValue() }

        then:
        source.isEmpty()
        reloadable.isEmpty()

        numInvocationsA == 1
        numInvocationsB == 1
        numInvocationsOnClear == 1

        when: "assign value to source reloadable many times"
        10.times { source.setValue(srcValueB) }

        then:
        source.isPresent()

        reloadable.isPresent()
        reloadable.get() == srcValueB

        numInvocationsA == 2
        numInvocationsB == 2
        numInvocationsOnClear == 1
    }

    def "predicate function throws: empty source reloadable on update "() {
        given:
        def srcValueA = 'blah'

        def numInvocationsA = 0
        def numInvocationsB = 0

        and:
        def reloadable = source
            .filter({ numInvocationsA++; it.length() > 0 })
            .filter({ numInvocationsB++; throw new RuntimeException("not today") })

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

    def "predicate function throws: non-empty source reloadable on update "() {
        given:
        def srcValueA = 'blah'
        def exception = new RuntimeException("not today")

        def numInvocationsA = 0
        def numInvocationsB = 0

        when:
        def reloadable = source
            .set(srcValueA)
            .filter({ numInvocationsA++; it.length() > 0 })
            .filter({ numInvocationsB++; throw exception })

        then:
        def ex = thrown(RuntimeException)
        ex.is(exception)

        source.isPresent()
        reloadable == null
    }

    def "updating source reloadable with a value that doesn't match the predicate should retain old value in filtered"() {
        given:
        def valueA = 'foobarbaz'
        def valueB = 'foo'

        and:
        def reloadable = source
            .set(valueA)
            .filter(predicate)

        expect:
        reloadable.isPresent()
        reloadable.get() == valueA

        when: "set value that doesn't match the predicate"
        source.set(valueB)

        then: "filtered reloadable should retain previous value"
        reloadable.isPresent()
        reloadable.get() == valueA
    }
}
