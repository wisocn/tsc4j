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

package com.github.tsc4j.core

import groovy.util.logging.Slf4j
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.Unroll

import java.util.function.Consumer

@Slf4j
@Unroll
@Stepwise
class AtomicInstanceSpec extends Specification {
    @Shared
    def expectedStr = 'čćžšđ ČĆŽŠĐ'

    @Shared
    int numDestroyerInvocations = 0

    @Shared
    def destroyedInstance = null

    @Shared
    Consumer<String> destroyer = { numDestroyerInvocations++; destroyedInstance = it }

    @Shared
    def instance = new AtomicInstance<>({ 'foo' }, destroyer)

    def "empty instance should not deliver any instance"() {
        expect:
        instance.get().isEmpty()
    }

    def "empty instance can't be closed"() {
        when:
        instance.close()

        then:
        numDestroyerInvocations == 0
        !instance.get().isPresent()
    }

    def "getOrCreate() should use constructor provided creator"() {
        given:
        def expectedStr = 'foobar'
        def instance = new AtomicInstance({ expectedStr }, {})

        expect:
        instance.getOrCreate() == expectedStr
    }

    def "getOrCreate() with custom supplier must take precedence over constructor provided creator"() {
        given:
        def instance = new AtomicInstance({ 'baz' }, {})
        def expectedStr = 'foobar'

        expect:
        instance.getOrCreate({ expectedStr }) == expectedStr
    }

    def "getOrCreate() should throw if supplier throws"() {
        given:
        def exception = new RuntimeException("boom!!!")

        when:
        def res = instance.getOrCreate({ throw exception })

        then:
        def ex = thrown(Throwable)
        ex.is(exception)

        res == null
        !instance.get().isPresent()
    }

    def "getOrCreate() should throw NPE if supplier returns null"() {
        when:
        def res = instance.getOrCreate({ null })

        then:
        thrown(NullPointerException)

        res == null
        !instance.get().isPresent()
    }

    def "getOrCreate() supplier should be called only once, from there on it should return the same instance"() {
        given:
        def numCalls = 20
        def supplierInvocations = 0
        def supplier = { supplierInvocations++; expectedStr }

        when: "call getOrCreate many times"
        def results = (1..numCalls).collect { instance.getOrCreate(supplier) }

        then:
        supplierInvocations == 1
        results.size() == numCalls

        instance.get().get().is(expectedStr)

        def first = results.first()
        first.is(expectedStr)
        results.each { assert it.is(first) }

        numDestroyerInvocations == 0
    }

    def "close() should call destroyer consumer"() {
        when:
        10.times { instance.close() }

        then:
        numDestroyerInvocations == 1
        destroyedInstance.is(expectedStr)

        !instance.get().isPresent()
    }

    def "close() should clear instance even if destroyer throws"() {
        given:
        def exception = new RuntimeException("boom!!!")
        def instance = new AtomicInstance({}, { throw exception })

        when: "create instance and close it"
        def result = instance.getOrCreate({ expectedStr })
        instance.close()

        then:
        result.is(expectedStr)
        !instance.get().isPresent()
    }

    def "instance with empty destroyer should behave the same"() {
        given:
        def instance = new AtomicInstance()

        expect:
        !instance.get().isPresent()

        when:
        def results = (1..10).collect { instance.getOrCreate({ expectedStr }) }
        def first = results.first()

        then:
        first.is(expectedStr)
        results.each { assert it.is(first) }
        instance.get().get().is(expectedStr)

        when:
        instance.close()

        then:
        !instance.get().isPresent()
    }
}
