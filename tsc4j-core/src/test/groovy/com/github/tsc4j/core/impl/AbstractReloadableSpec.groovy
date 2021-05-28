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

import com.github.tsc4j.api.Reloadable
import com.github.tsc4j.testsupport.BaseSpec
import groovy.util.logging.Slf4j
import spock.lang.Unroll

import java.util.function.Consumer

@Slf4j
@Unroll
abstract class AbstractReloadableSpec extends BaseSpec {
    protected abstract <T> Reloadable<T> emptyReloadable()

    protected abstract <T> Reloadable<T> createReloadable(T value)

    protected abstract <T> Reloadable<T> updateReloadable(Reloadable<T> reloadable, T value)

    protected abstract <T> Reloadable<T> removeReloadableValue(Reloadable<T> reloadable)

    def "empty reloadable should not contain value"() {
        given:
        def reloadable = emptyReloadable()

        expect: "should not contain value"
        !reloadable.isPresent()
        reloadable.isEmpty()

        when: "try to extract value anyway"
        def value = reloadable.get()

        then:
        thrown(NoSuchElementException)
    }

    def "orElse() should allow nulls to be passed"() {
        when:
        def res = emptyReloadable().orElse(null)

        then:
        noExceptionThrown()
        res == null
    }

    def "orElse() should return argument on empty reloadable"() {
        given:
        def defaultValue = 'default-value'
        def reloadable = emptyReloadable()

        expect:
        reloadable.orElse(defaultValue).is(defaultValue)
    }

    def "orElse() should return stored value on non-empty reloadable"() {
        given:
        def defaultValue = 'default-value'
        def rValue = 'foo'
        def reloadable = createReloadable(rValue)

        expect:
        reloadable.orElse(defaultValue) == rValue
    }

    def "orElseGet() should not allow null suppliers"() {
        when:
        def res = emptyReloadable().orElseGet(null)

        then:
        thrown(NullPointerException)
        res == null
    }

    def "orElseGet() should return supplier's generated value on empty reloadable"() {
        given:
        def defaultValue = 'default-value'
        def reloadable = emptyReloadable()

        expect:
        reloadable.orElseGet({ defaultValue }).is(defaultValue)
    }

    def "orElseGet() should return null if supplier returns null"() {
        given:
        def reloadable = emptyReloadable()

        expect:
        reloadable.orElseGet({ null }) == null
    }

    def "orElseGet() should return stored value on non-empty reloadable"() {
        given:
        def defaultValue = 'default-value'
        def rValue = 'foo'
        def reloadable = createReloadable(rValue)

        expect:
        reloadable.orElseGet({ defaultValue }) == rValue
    }


    def "non-empty reloadable should contain value"() {
        given:
        def reloadable = createReloadable("foo")

        expect:
        reloadable.isPresent()
        !reloadable.isEmpty()
    }

    def "new reloadable should contain no update consumer"() {
        given:
        def reloadable = createReloadable("foo")

        expect:
        reloadable.registered() != null
        reloadable.registered().isEmpty()
    }

    def "onUpdate(Consumer) should not add duplicates"() {
        given:
        def reloadable = emptyReloadable()
        def receivedValue = null
        def consumer = { receivedValue = it } as Consumer

        when:
        def r = reloadable.register(consumer)

        then:
        r.is(reloadable)
        receivedValue == null // should not invoke

        when:
        10.times { reloadable.register(consumer) }

        then:
        receivedValue == null
        reloadable.registered().size() == 1
        reloadable.registered().get(0).is(consumer)
    }

    def "onUpdate() should return list copy"() {
        given:
        def reloadable = emptyReloadable()

        and: "setup consumers"
        def consumerA = { log.info("A consumed: $it") } as Consumer
        def consumerB = { log.info("B consumed: $it") } as Consumer
        def consumerC = { log.info("C consumed: $it") } as Consumer

        when: "apply consumers"
        def r = reloadable.register(consumerA).register(consumerB).register(consumerC)
        def registered = reloadable.registered()

        then: "all consumers should be registered"
        r.is(reloadable)

        registered.size() == 3
        registered.contains(consumerA)
        registered.contains(consumerB)
        registered.contains(consumerC)

        when: "let's alter returned list"
        registered.clear()
        def stillRegistered = reloadable.registered()

        then: "reloadable should still have all consumers registered"
        stillRegistered.size() == 3
        stillRegistered.contains(consumerA)
        stillRegistered.contains(consumerB)
        stillRegistered.contains(consumerC)
    }

    def "ifPresent() should throw in case of null arguments"() {
        when:
        createReloadable().ifPresent(null)

        then:
        thrown(NullPointerException)

        where:
        reloadable << [createReloadable("foo"), emptyReloadable()]
    }

    def "ifPresent() should invoke consumer when value is present"() {
        given:
        def value = "foo"
        def receivedValue = null
        def reloadable = createReloadable(value)

        when:
        def r = reloadable.ifPresent({ receivedValue = it })

        then:
        r.is(reloadable)

        receivedValue != null
        receivedValue == value
        receivedValue.is(value)
    }

    def "ifPresent() should not invoke consumer when value is not present"() {
        given:
        def reloadable = emptyReloadable()

        and: "setup consumer"
        def receivedValue = null
        def invoked = false

        when:
        def r = reloadable.ifPresent({ receivedValue = it; invoked = true })

        then:
        r.is(reloadable)

        !invoked
        receivedValue == null
    }

    def "ifPresentAndRegister() should throw on null arguments"() {
        when:
        createReloadable().ifPresentAndRegister(null)

        then:
        thrown(NullPointerException)

        where:
        reloadable << [createReloadable("foo"), emptyReloadable()]
    }

    def "ifPresentAndRegister() should invoke consumer when value is present and it should register it"() {
        given:
        def value = "foo"
        def reloadable = createReloadable(value)

        and: "setup consumer"
        def receivedValue = null
        def invoked = false
        def consumer = { receivedValue = it; invoked = true } as Consumer

        when:
        def r = reloadable.ifPresentAndRegister(consumer)

        then:
        r.is(reloadable)

        invoked // should invoke consumer
        receivedValue.is(value)

        reloadable.registered().size() == 1
        reloadable.registered().get(0).is(consumer)
    }

    def "ifPresentAndRegister() should not invoke consumer when value is not present, but it should register it anyway"() {
        given:
        def reloadable = emptyReloadable()

        and: "setup consumer"
        def receivedValue = null
        def invoked = false
        def consumer = { receivedValue = it; invoked = true } as Consumer

        when:
        def r = reloadable.ifPresentAndRegister(consumer)

        then:
        r.is(reloadable)

        !invoked // should not invoke consumer
        receivedValue == null

        reloadable.registered().size() == 1
        reloadable.registered().get(0).is(consumer)
    }

    def "ifPresentAndRegister() should not register consumer if value is present and consumer throws exception"() {
        given:
        def value = "foo"
        def reloadable = createReloadable(value)
        def exception = new RuntimeException("trololo")

        def invoked = false
        def consumedValue = null
        def consumer = { invoked = true; consumedValue = it; throw exception } as Consumer

        when:
        def r = reloadable.ifPresentAndRegister(consumer)

        then:
        def thrown = thrown(exception.getClass())

        r == null
        thrown.is(exception)

        invoked == true
        consumedValue == value

        reloadable.registered().isEmpty()
    }

    def "removing value should run consumers on reloadable that contains value"() {
        given:
        def reloadable = createReloadable("foo")

        def consumedValue = "bar"
        def invoked = false

        and: "setup consumer"
        reloadable.register({ invoked = true; consumedValue = it })

        when:
        removeReloadableValue(reloadable)

        then:
        reloadable.isEmpty()
        !reloadable.isPresent()

        invoked == true
        consumedValue == null
    }

    def "onUpdate should register multiple consumers"() {
        given:
        def aInvoked = false
        def consumerA = { aInvoked = true } as Consumer
        def bInvoked = false
        def consumerB = { aInvoked = true } as Consumer

        when:
        def r = reloadable.register(consumerA).register(consumerB)

        then:
        r.is(reloadable)

        reloadable.registered().size() == 2
        reloadable.registered() == [consumerA, consumerB]

        aInvoked == false
        bInvoked == false

        when: "changing returned list should not have any effect on reloadable registered consumers"
        def consumerList = reloadable.registered()
        consumerList.clear()

        then:
        aInvoked == false
        bInvoked == false
        reloadable.registered() == [consumerA, consumerB]

        where:
        reloadable << [emptyReloadable(), createReloadable("foo")]
    }

    def "close() should unregister consumers"() {
        given:
        def aInvoked = false
        def consumerA = { aInvoked = true } as Consumer
        def bInvoked = false
        def consumerB = { aInvoked = true } as Consumer

        when: "register consumers"
        def r = reloadable.register(consumerA).register(consumerB)

        then:
        r.is(reloadable)

        reloadable.registered() == [consumerA, consumerB]

        when: "unregister all consumers"
        reloadable.close()

        then:
        aInvoked == false
        bInvoked == false

        reloadable.registered().isEmpty()

        where:
        reloadable << [emptyReloadable(), createReloadable("foo")]
    }

    def "setValue() should throw on null arguments"() {
        when:
        def r = reloadable.setValue(null)

        then:
        thrown(NullPointerException)
        r == null

        where:
        reloadable << [emptyReloadable(), createReloadable("foo")]
    }

    def "setValue() should set value and not run consumers/update number of updates on empty reloadable"() {
        given:
        def updatedValue = "foo"
        def reloadable = emptyReloadable()

        and: "setup reloadable"
        def aReceived = null
        def consumerA = { aReceived = it } as Consumer

        reloadable.register(consumerA)

        expect:
        !reloadable.isPresent()
        aReceived == null
        reloadable.getNumUpdates() == 0

        when:
        reloadable.get()

        then:
        thrown(NoSuchElementException)

        when: "set value"
        def r = reloadable.setValue(updatedValue)

        then:
        r.is(reloadable)
        reloadable.get().is(updatedValue)
        reloadable.getNumUpdates() == 1

        aReceived == updatedValue // update consumer should not be invoked
    }

    def "setValue() should set new value and run consumers"() {
        given:
        def initialValue = "foo"
        def updatedValue = "bar"
        def reloadable = createReloadable(initialValue)

        and: "setup up consumers"
        def aReceived = null
        def consumerA = { aReceived = it } as Consumer
        def bReceived = null
        def consumerB = { bReceived = it; throw new RuntimeException("trololo") } as Consumer
        def cReceived = null
        def consumerC = { cReceived = it } as Consumer

        reloadable.register(consumerA).register(consumerB).register(consumerC)

        expect:
        reloadable.getNumUpdates() == 1
        reloadable.isPresent()
        reloadable.get() == initialValue

        when: "update value"
        def r = reloadable.setValue(updatedValue)

        then:
        r.is(reloadable)

        reloadable.isPresent() // value should be present
        reloadable.get().is(updatedValue)
        reloadable.getNumUpdates() == 2

        aReceived.is(updatedValue) // consumers should be invoked
        bReceived.is(updatedValue)
        cReceived.is(updatedValue)
    }

    def "setValue() should set new value and run consumers only if new value is different from previous one"() {
        given:
        def initialValue = "foo"
        def updatedValue = "bar"
        def reloadable = createReloadable(initialValue)

        and:
        def aReceived = null
        def bReceived = null

        reloadable.register({ aReceived = it }).register({ bReceived = it })

        expect:
        aReceived == null
        bReceived == null

        when: "set the same value again"
        reloadable.setValue(initialValue)

        then:
        aReceived == null
        bReceived == null

        when: "set new, different value"
        reloadable.setValue(updatedValue)

        then:
        aReceived.is(updatedValue)
        bReceived.is(updatedValue)
    }

    def "removeValue() should return empty optional on empty reloadable and not run onUpdate consumers"() {
        given:
        def initValue = "foo"
        def numCalled = 0
        def updatedValue = initValue

        def reloadable = emptyReloadable().register({ numCalled++; updatedValue = it })

        expect:
        !reloadable.removeValue().isPresent()
        reloadable.getNumUpdates() == 0
        numCalled == 0 // should not call value update consumers if was not present in the first place
        updatedValue == initValue
    }

    def "removeValue() should remove value from reloadable with value present and run onUpdate consumers"() {
        given:
        def consumerCalled = 0
        def consumerUpdatedValue = "foo"

        def value = "foo"
        def reloadable = createReloadable(value).register({ consumerCalled++; consumerUpdatedValue = it })

        def initialNumUpdates = reloadable.getNumUpdates()

        expect:
        reloadable.isPresent()
        reloadable.getNumUpdates() == 1
        reloadable.get() == value

        when:
        def valueOpt = reloadable.removeValue()

        then:
        !reloadable.isPresent()
        reloadable.getNumUpdates() == initialNumUpdates + 1

        valueOpt.isPresent()
        valueOpt.get() == value

        consumerCalled == 1
        consumerUpdatedValue == null
    }

    def "once reloadable with value present is closed it should no longer contain value and registered consumers"() {
        given:
        def numA = 0
        def consumerA = { numA++; log.info("consumerA: $it") } as Consumer<String>
        def numB = 0
        def consumerB = { numB++; log.info("consumerB: $it") } as Consumer<String>
        def value = UUID.randomUUID().toString()
        def reloadable = createReloadable(value).register(consumerA)
                                                .register(consumerB)
        expect:
        reloadable.isPresent()
        !reloadable.isEmpty()
        reloadable.get().is(value)
        reloadable.registered() == [consumerA, consumerB]
        numA == 0
        numB == 0

        when: "close reloadable"
        reloadable.close()

        then:
        !reloadable.isPresent()
        reloadable.isEmpty()
        reloadable.registered().isEmpty()

        when: "fetching the value should throw no such element exception"
        def storedValue = reloadable.get()

        then:
        thrown(NoSuchElementException)
        storedValue == null

        when: "setting value should throw illegal state exception"
        updateReloadable(reloadable, "foo")

        then:
        thrown(IllegalStateException)
        !reloadable.isPresent()
        reloadable.isEmpty()
        reloadable.registered().isEmpty()

        when: "removing a value should throw illegal state exception"
        removeReloadableValue(reloadable)

        then:
        thrown(IllegalStateException)
        !reloadable.isPresent()
        reloadable.isEmpty()
        reloadable.registered().isEmpty()

        when: "registering new consumer should throw illegal state exception"
        reloadable.register(consumerA)

        then:
        thrown(IllegalStateException)
        !reloadable.isPresent()
        reloadable.isEmpty()
        reloadable.registered().isEmpty()
        numA == 0
        numB == 0

        when: "ifPresentAndRegister should throw illegal state exception and no consumer should be invoked"
        reloadable.ifPresentAndRegister(consumerB)

        then:
        thrown(IllegalStateException)
        !reloadable.isPresent()
        reloadable.isEmpty()
        reloadable.registered().isEmpty()
        numA == 0
        numB == 0

        when: "ifPresent() should not invoke consumer"
        reloadable.ifPresent(consumerA)

        then:
        numA == 0
        numB == 0

        when: "closing reloadable should result in empty list of registered consumers"
        reloadable.close()

        then:
        reloadable.registered().isEmpty()

        when: "closing reloadable again should have no effect"
        reloadable.close()

        then:
        !reloadable.isPresent()
        reloadable.isEmpty()
        reloadable.registered().isEmpty()
        numA == 0
        numB == 0
    }

    def "once empty reloadable is closed it should no longer contain registered consumers"() {
        given:
        def numA = 0
        def consumerA = { numA++; log.info("consumerA: $it") } as Consumer<String>
        def numB = 0
        def consumerB = { numB++; log.info("consumerB: $it") } as Consumer<String>
        def reloadable = emptyReloadable().register(consumerA)
                                          .register(consumerB)
        expect:
        !reloadable.isPresent()
        reloadable.isEmpty()
        reloadable.registered() == [consumerA, consumerB]
        numA == 0
        numB == 0

        when: "close reloadable"
        reloadable.close()

        then:
        !reloadable.isPresent()
        reloadable.isEmpty()
        reloadable.registered().isEmpty()

        when: "fetching the value should throw no such element exception"
        def storedValue = reloadable.get()

        then:
        thrown(NoSuchElementException)
        storedValue == null

        when: "setting value should throw illegal state exception"
        updateReloadable(reloadable, "foo")

        then:
        thrown(IllegalStateException)
        !reloadable.isPresent()
        reloadable.isEmpty()
        reloadable.registered().isEmpty()

        when: "removing a value should throw illegal state exception"
        removeReloadableValue(reloadable)

        then:
        thrown(IllegalStateException)
        !reloadable.isPresent()
        reloadable.isEmpty()
        reloadable.registered().isEmpty()

        when: "registering new consumer should throw illegal state exception"
        reloadable.register(consumerA)

        then:
        thrown(IllegalStateException)
        !reloadable.isPresent()
        reloadable.isEmpty()
        reloadable.registered().isEmpty()
        numA == 0
        numB == 0

        when: "ifPresentAndRegister should throw illegal state exception and no consumer should be invoked"
        reloadable.ifPresentAndRegister(consumerB)

        then:
        thrown(IllegalStateException)
        !reloadable.isPresent()
        reloadable.isEmpty()
        reloadable.registered().isEmpty()
        numA == 0
        numB == 0

        when: "ifPresent() should not invoke consumer"
        reloadable.ifPresent(consumerA)

        then:
        numA == 0
        numB == 0

        when: "close() should close reloadable and unregister all update consumers"
        reloadable.close()

        then:
        !reloadable.isPresent()
        reloadable.isEmpty()
        reloadable.registered().isEmpty()

        when: "closing reloadable again should have no effect"
        reloadable.close()

        then:
        !reloadable.isPresent()
        reloadable.isEmpty()
        reloadable.registered().isEmpty()
        numA == 0
        numB == 0
    }
}
