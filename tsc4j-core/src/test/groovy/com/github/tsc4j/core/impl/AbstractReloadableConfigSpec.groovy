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

import com.github.tsc4j.core.Tsc4jException
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import groovy.transform.ToString
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

@Unroll
abstract class AbstractReloadableConfigSpec<T extends AbstractReloadableConfig> extends Specification {
    protected Logger log = LoggerFactory.getLogger(getClass())

    protected volatile Config updatedConfig = null

    /**
     * Creates reloadable config for testing.
     * @param reverseUpdateOrder update reloadables in reverse order?
     */
    protected T createReloadableConfig(boolean reverseUpdateOrder = false) {
        createReloadableConfig({ ConfigFactory.empty() }, reverseUpdateOrder)
    }

    /**
     * Creates reloadable config for testing.
     * @param configuration supplier.
     * @param reverseUpdateOrder update reloadables in reverse order?
     */
    protected final T createReloadableConfig(Supplier<Config> configSupplier,
                                             boolean reverseUpdateOrder = false) {
        def supplier = { updatedConfig ? updatedConfig : configSupplier.get() } as Supplier<Config>
        doCreateReloadableConfig(supplier, reverseUpdateOrder)
    }

    /**
     * Creates reloadable config for testing.
     * @param configuration supplier.
     * @param reverseUpdateOrder update reloadables in reverse order?
     */
    protected abstract T doCreateReloadableConfig(Supplier<Config> configSupplier,
                                                  boolean reverseUpdateOrder = false)

    /**
     * Updates internal config state in reloadable config.
     * @param rc reloadable config
     * @param configMap configuration map to assign.
     * @return reference to given reloadable config
     */
    protected T updateConfig(T rc, Map<String, Object> configMap) {
        updateConfig(rc, ConfigFactory.parseMap(configMap))
    }

    /**
     * Updates internal config state in reloadable config.
     * @param rc reloadable config
     * @param config config instance to assign.
     * @return reference to given reloadable config
     */
    protected T updateConfig(T rc, Config config) {
        this.updatedConfig = config
        rc
    }

    def "supplier that returns null configuration should not assign any config"() {
        when:
        def called = false
        def rc = createReloadableConfig({ called = true; null })

        then:
        rc != null
        !rc.isPresent()
        called == true

        when: "try again"
        def future = rc.refresh()

        then:
        !rc.isPresent()

        when: "wait for completion"
        def completableFut = future.toCompletableFuture()
        completableFut.get(1, TimeUnit.SECONDS)

        then:
        def thrown = thrown(ExecutionException)
        completableFut.isCompletedExceptionally()

        def cause = thrown.getCause()
        cause instanceof IllegalStateException
        cause.getMessage().contains(" returned null")
    }

    def "get() should return failed future for reloadable config with supplier that throws"() {
        given:
        def exception = new RuntimeException("foo")
        def supplier = { throw exception } as Supplier<Config>

        when:
        def rc = createReloadableConfig(supplier)

        then:
        noExceptionThrown()
        rc != null

        !rc.isPresent()
        rc.getNumUpdates() == 0

        when: "ask for config in async manner"
        def future = rc.get().toCompletableFuture()

        then:
        future != null
        future.isDone()
        !future.isCancelled()

        !rc.isPresent()
        rc.getNumUpdates() == 0
    }

    def "getSync() should throw Tsc4jException for reloadable config with supplier that throws"() {
        given:
        def exception = new RuntimeException("foo")
        def supplier = { throw exception } as Supplier<Config>

        when:
        def rc = createReloadableConfig(supplier)

        then:
        noExceptionThrown()
        rc != null

        !rc.isPresent()
        rc.getNumUpdates() == 0

        when: "ask for a config in a blocking manner"
        def syncConfig = rc.getSync()

        then:
        def thrown = thrown(Tsc4jException)
        thrown.getMessage().contains("Error fetching configuration:")
        thrown.getCause().is(exception)
        syncConfig == null

        !rc.isPresent()
        rc.getNumUpdates() == 0
    }

    def "reloadable config with supplier that returns empty config should perform automatic refresh on construction"() {
        given:
        def rc = createReloadableConfig(false)

        when:
        def future = rc.get().toCompletableFuture()
        Thread.sleep(30) // ... wait for background thread to do it's job

        then:
        future.isDone()
        !future.isCancelled()
        future.get().isEmpty()
        rc.getSync().isEmpty()
        rc.getSync().is(future.get())

        rc.isPresent()
        rc.getNumUpdates() == 1
    }

    def "should not increment update count "() {
        given:
        def attempts = 10
        def empty = ConfigFactory.empty()
        def rc = createReloadableConfig(false)

        when: "refresh config n times"
        attempts.times { rc.assignConfig(empty) }
        log.info("got: {}", rc)

        then:
        rc.isPresent()
        rc.getSync().isEmpty()
        rc.getNumUpdates() == 1
    }

    def "new instance should contain configuration"() {
        when:
        def rc = createReloadableConfig(false)
        log.info("created: {}", rc)

        then:
        rc.isPresent()
        rc.getNumUpdates() == 1

        // as for config future
        def future = rc.get().toCompletableFuture()
        future.isDone()
        !future.isCancelled()
        future.get() == ConfigFactory.empty()

        // ask for config directly
        rc.getSync() == ConfigFactory.empty()
    }

    def "new instance should immediately trigger supplier"() {
        given:
        def counter = new AtomicInteger()
        def map = [a: UUID.randomUUID().toString(), foo: 42]
        def config = ConfigFactory.parseMap(map)
        def supplier = { counter.incrementAndGet(); config } as Supplier<Config>

        and: "setup rc"
        def rc = createReloadableConfig(supplier)
        log.info("created: {} -> {}", rc, counter)

        expect:
        rc.isPresent()
        rc.getNumUpdates() == 1

        rc.getSync().root().unwrapped() == map
        rc.get().toCompletableFuture().isDone()

        when: "get() should always return singleton"
        def getFutures = (1..10).collect { rc.get() }

        then: "all get futures should be the same instance"
        getFutures.every { it.is(getFutures[0]) }

        when: "ask for config synchronously"
        def configs = (1..10).collect { rc.getSync() }

        then: "all configs should be the same instance"
        configs.every { it.is(configs[0]) }

        when: "trigger few refreshes"
        (1..10).collect { rc.get().toCompletableFuture().get() }

        then: "there refresh flag should be reset"
        rc.getNumUpdates() == 1
        rc.isRefreshRunning() == false
    }

    def "should update reloadables during refreshes"() {
        given:
        def mapA = [:]

        // setup updated config (after refresh)
        def mapB = [
            persons: [
                a: [name: 'Barry', surname: 'Lyndon'],
                b: [name: 'Joe', surname: 'Average'],
            ]
        ]

        def mapC = [
            persons: [
                b: [name: 'Johnny', surname: 'Bravo'],
            ]
        ]

        and: "setup config supplier"
        def count = 0
        def supplier = {
            def map = mapA
            if (count > 0 && count <= 2) {
                map = mapB
            }
            if (count > 2) {
                map = mapC
            }
            log.info("config supplier invocation: {} -> {}", count, map)
            count++
            ConfigFactory.parseMap(map)
        } as Supplier<Config>

        and: "setup reloadable config"
        def rc = createReloadableConfig(supplier)

        and: "setup reloadables"
        def reloadableA = rc.register("persons.a", Person)
        def reloadableB = rc.register("persons.b", Person)

        expect: "supplier will return mapA"
        count == 1
        rc.isPresent()
        rc.getNumUpdates() == 1
        rc.getSync().isEmpty()

        // reloadables should be empty
        !reloadableA.isPresent()
        reloadableA.isEmpty()

        !reloadableB.isPresent()
        reloadableB.isEmpty()

        when: "refresh config, supplier will return mapB"
        def future = rc.refresh().toCompletableFuture()
        future.get()

        then:
        count == 2
        rc.isPresent()
        rc.getNumUpdates() == 2

        future.get().root().unwrapped() == mapB

        reloadableA.isPresent()
        !reloadableA.isEmpty()

        reloadableB.isPresent()
        !reloadableB.isEmpty()

        when: "extract beans from reloadables"
        def personA = reloadableA.get()
        def personB = reloadableB.get()

        then: "beans should contain expected values"
        personA.getName() == 'Barry'
        personA.getSurname() == 'Lyndon'

        personB.getName() == 'Joe'
        personB.getSurname() == 'Average'

        when: "refresh config again, supplier will return mapB (same config didn't change)"
        future = rc.refresh().toCompletableFuture()
        future.get()

        then:
        count == 3
        rc.isPresent()
        rc.getNumUpdates() == 2

        future.get().root().unwrapped() == mapB

        // bean instances should not change
        reloadableA.get().is(personA)
        reloadableB.get().is(personB)

        when: "refresh config again, supplier will return mapC (config changed)"
        future = rc.refresh().toCompletableFuture()
        future.get()

        then:
        count == 4
        rc.isPresent()
        rc.getNumUpdates() == 3

        future.get().root().unwrapped() == mapC

        // first reloadable should be empty, second one should contain different value
        !reloadableA.isPresent()
        reloadableB.isPresent()
        reloadableB.get() != personB

        reloadableB.get().getName() == 'Johnny'
        reloadableB.get().getSurname() == 'Bravo'
    }

    def "should update reloadables in correct order (reverse: #reverse)"() {
        given:
        def map = [
            persons: [
                a: [name: 'Barry', surname: 'Lyndon'],
                b: [name: 'Joe', surname: 'Average']
            ]
        ]

        and: "setup rc"
        def rc = createReloadableConfig(reverse)
        rc.getSync()

        and: "setup reloadables"
        def updates = new CopyOnWriteArrayList<String>()
        def reloadableA = rc.register('persons.a', Person)
                            .register({ updates.add('a') })

        def reloadableB = rc.register('persons.a', Person)
                            .register({ updates.add('b') })

        def reloadableC = rc.register('persons.b', Person)
                            .register({ updates.add('c') })

        expect:
        rc.isPresent()

        !reloadableA.isPresent()
        !reloadableB.isPresent()
        !reloadableC.isPresent()
        updates.isEmpty()

        when: "assign config"
        updateConfig(rc, map)

        log.info("got config: {}", rc.getSync())

        then:
        reloadableA.isPresent()
        reloadableB.isPresent()
        reloadableC.isPresent()

        // order is important
        if (reverse) {
            assert updates == ['c', 'b', 'a']
        } else {
            assert updates == ['a', 'b', 'c']
        }

        where:
        reverse << [false, true]
    }

    def "closing reloadable config should close created reloadables as well"() {
        given:
        def rc = createReloadableConfig()

        expect:
        rc.isPresent()
        !rc.isClosed()
        rc.size() == 0

        when: "register reloadables"
        def reloadableA = rc.register('persons.a', Person)
        def reloadableB = rc.register('persons.b', Person)

        then:
        rc.isPresent()
        !rc.isClosed()
        rc.size() == 2

        !reloadableA.isPresent()
        !reloadableA.isClosed()

        !reloadableB.isPresent()
        !reloadableB.isClosed()

        when: "close reloadable config"
        rc.close()

        then:
        rc.isPresent()
        rc.isClosed()
        rc.size() == 0

        reloadableA.isClosed()
        reloadableB.isClosed()

        when: "checkClosed() should throw ISE"
        rc.checkClosed()

        then:
        thrown(IllegalStateException)

        when: "trying to close it again should execute without exception"
        rc.close()

        then:
        noExceptionThrown()

        when: "try to register new handlers on reloadableA"
        reloadableA.register({})

        then:
        def exA = thrown(IllegalStateException)
        exA.getMessage().toLowerCase().contains("instance is closed")

        when: "try to register new handlers on reloadableA"
        reloadableB.register({})

        then:
        def exB = thrown(IllegalStateException)
        exB.getMessage().toLowerCase().contains("instance is closed")
    }

    @ToString(includePackage = false, includeNames = true)
    static class Person {
        String name
        String surname
    }
}
