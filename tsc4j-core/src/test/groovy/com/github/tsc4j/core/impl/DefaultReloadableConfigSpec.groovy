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

import beans.java.immutable.ImmutableBean
import com.github.tsc4j.core.AggConfigSource
import com.github.tsc4j.core.ConfigSource
import com.github.tsc4j.core.Tsc4jException
import com.github.tsc4j.testsupport.TestConfigSource
import com.github.tsc4j.testsupport.TestConstants
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import com.typesafe.config.ConfigValueType
import spock.lang.Unroll

import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function
import java.util.function.Supplier

import static com.github.tsc4j.testsupport.TestConstants.defaultConfigQuery

@Unroll
class DefaultReloadableConfigSpec extends AbstractReloadableConfigSpec<DefaultReloadableConfig> {
    private static final defaultRefreshInterval = Duration.ofDays(42)

    private def configSupplier = { ConfigFactory.empty() } as Supplier<Config>
    private def defConfigSupplier = {
        TestConfigSource.createConfigSource().get(TestConstants.defaultConfigQuery)
    } as Supplier<Config>

    def "config creation should work"() {
        given:
        def prefix = "test.bean."
        when:
        def config = TestConfigSource.createConfig()//createConfig(configMap)

        then:
        config.getString("foo") == "bar"
        config.getValue("test").valueType() == ConfigValueType.OBJECT
        config.getBoolean("${prefix}aBoolean") == true
        config.getInt("${prefix}aInt") == 42
        config.getLong("${prefix}aLong") == 667
        config.getDouble("${prefix}aDouble") == 42.667
        config.getString("${prefix}aString") == "foo"
        config.getString("${prefix}extString") == "bar"
    }

    def "should correctly sort reloadables"() {
        given:
        def reloadableA = new DefaultReloadable(1, "a.a.a", Function.identity(), {})
        def reloadableB = new DefaultReloadable(1, "a.c.a", Function.identity(), {})
        def reloadableC = new DefaultReloadable(1, "a.b.a", Function.identity(), {})
        def rc = DefaultReloadableConfig.builder()
                                        .configSupplier(configSupplier)
                                        .refreshInterval(defaultRefreshInterval)
                                        .reverseUpdateOrder(false)
                                        .build()

        when:
        def result = rc.sortReloadables([reloadableA, reloadableB, reloadableC])

        then:
        result == [reloadableA, reloadableC, reloadableB]

        cleanup:
        rc?.close()
    }

    def "should sort correctly reloadables in reverse order"() {
        given:
        def reloadableA = new DefaultReloadable(1, "a.a.a", Function.identity(), {})
        def reloadableB = new DefaultReloadable(2, "a.c.a", Function.identity(), {})
        def reloadableC = new DefaultReloadable(2, "a.b.a", Function.identity(), {})

        def rc = DefaultReloadableConfig.builder()
                                        .configSupplier(configSupplier)
                                        .refreshInterval(defaultRefreshInterval)
                                        .reverseUpdateOrder(true)
                                        .build()

        when:
        def result = rc.sortReloadables([reloadableA, reloadableB, reloadableC])

        then:
        result == [reloadableB, reloadableC, reloadableA]

        cleanup:
        rc?.close()
    }

    def "should properly extract boolean property"() {
        given:
        def rc = createRc()
        when:
        def reloadable = rc.register("test.bean.aBoolean", beanClass)

        then:
        reloadable.isPresent()
        reloadable.get() instanceof Boolean
        reloadable.get() == true

        cleanup:
        rc?.close()

        where:
        beanClass << [boolean.class, Boolean.class]
    }

    def "should properly extract integer property"() {
        given:
        def rc = createRc()

        when:
        def reloadable = rc.register("test.bean.aInt", beanClass)

        then:
        reloadable.isPresent()
        reloadable.get() instanceof Integer
        reloadable.get() == 42

        cleanup:
        rc?.close()

        where:
        beanClass << [int.class, Integer.class]
    }

    def "should properly reload integer property"() {
        given:
        def configPath = "test.bean.aInt"
        def updatedValue = 67

        def rc = createRc()

        when: "register reloadable"
        def reloadable = rc.register(configPath, beanClass)

        then:
        reloadable.isPresent()
        reloadable.get() instanceof Integer
        reloadable.get() == 42

        when: "update config and reload config"
        def map = TestConfigSource.defaultCfgMap()
        map.test.bean.aInt = updatedValue

        updateConfig(rc, map)

        // issue refresh request
        rc.refresh().toCompletableFuture().get()

        then: "reloadable should be updated with new value"
        reloadable.isPresent()
        reloadable.get() instanceof Integer
        reloadable.get() == updatedValue

        cleanup:
        rc?.close()

        where:
        //beanClass << intClasses()
        beanClass << [int.class, Integer.class]
    }

    def "should preserve original config if subsequent fetch fails and run onError handlers only when error occurs"() {
        given: "setup vars"
        def key = "foo"
        def value = "bar"
        def refreshIntevalMillis = 101
        def numRefreshIntervalsToWait = 3

        and: "setup stackable config supplier"
        // first supplier always returns configuration
        def sourceA = {
            ConfigFactory.empty().withValue(key, ConfigValueFactory.fromAnyRef(value))
        } as ConfigSource

        // second supplier returns configuration on first fetch, but after that always fails
        def exception = new RuntimeException("error")
        // java9 groovy workaround; groovy claims that it cannot create find RuntimeException constructor when sourceB
        // is supposed to throw exception.
        exception.toString()
        def counter = new AtomicInteger()
        def sourceB = {
            if (counter.incrementAndGet() > 1) {
                throw exception
            }
            ConfigFactory.empty()
        } as ConfigSource

        def configSource = AggConfigSource.builder()
                                          .source(sourceA)
                                          .source(sourceB)
                                          .build()

        and: "setup reloadable config"
        def configSupplier = new ConfigSupplier(configSource, defaultConfigQuery)
        def rc = DefaultReloadableConfig.builder()
                                        .configSupplier(configSupplier)
                                        .refreshInterval(Duration.ofMillis(refreshIntevalMillis))
                                        .refreshJitterPct(0)
                                        .build()

        when: "fetch configuration"
        def config = rc.getSync()
        log.info("first fetch done.")

        then:
        !config.isEmpty()
        config.getString(key) == value

        when:
        "wait few refresh intervals and fetch config again"
        log.info("sleeping.")
        Thread.sleep(numRefreshIntervalsToWait * refreshIntevalMillis)
        log.info("sleep done")
        def newConfig = rc.getSync()

        then:
        "new config should be the same as configuration in the first fetch, because subsequent fetches failed"
        newConfig == config
        newConfig.is(config)

        cleanup:
        rc?.close()
    }

    def "should gracefully handle first fetch failure and be able to assign first successfully fetched config"() {
        given: "setup vars"
        def key = "foo"
        def value = "bar"
        def refreshIntevalMillis = 101
        def numRefreshIntervalsToWait = 3

        and: "setup config supplier"
        def counter = new AtomicInteger()
        def exception = new RuntimeException("error")
        def configSupplier = {
            log.info("will supply config - maybe.")
            if (counter.incrementAndGet() < 3) {
                throw exception
            }
            ConfigFactory.empty().withValue(key, ConfigValueFactory.fromAnyRef(value))
        } as Supplier<Config>

        and: "setup reloadable config"
        def rc = DefaultReloadableConfig.builder()
                                        .configSupplier(configSupplier)
                                        .refreshInterval(Duration.ofMillis(refreshIntevalMillis))
                                        .refreshJitterPct(0)
                                        .build()

        when: "fetch config for the first time"
        def config = rc.getSync()

        then:
        def thrown = thrown(Tsc4jException)
        thrown.getCause() == exception
        counter.get() == 1

        config == null

        when: "wait few refresh cycles, config should be available"
        Thread.sleep(numRefreshIntervalsToWait * refreshIntevalMillis)
        config = rc.getSync()

        then:
        config.getString(key) == value
        counter.get() >= 3

        cleanup:
        rc?.close()
    }

    def "register(Class) should throw on null arguments"() {
        given:
        def rc = createReloadableConfig()

        when:
        def reloadable = rc.register((Class<?>) null)

        then:
        thrown(NullPointerException)
        reloadable == null

        cleanup:
        rc?.close()
    }

    def "register(Class) should throw if bean class is not annotated with @Tsc4jConfigPath"() {
        given:
        def rc = createReloadableConfig()

        when:
        def reloadable = rc.register((Class<?>) clazz)

        then:
        thrown(IllegalArgumentException)
        reloadable == null

        cleanup:
        rc?.close()

        where:
        clazz << [Integer, String, int.class]
    }

    def "register(Class) should register correct reloadable"() {
        given:
        //def rc = createReloadableConfig()
        def rc = createRc()

        when:
        //log.info("config: {}", Tsc4j.render(rc.getSync(), true))
        def reloadable = rc.register(ImmutableBean)

        then:
        reloadable != null
        reloadable.isPresent()

        when:
        def bean = reloadable.get()

        then:
        bean != null
        bean.isABoolean()
        bean.getADouble() == 42.667

        cleanup:
        rc?.close()
    }

    def intClasses() {
        def result = []
        1000.times {
            result.add(int.class)
            result.add(Integer.class)
        }
        result
    }

    @Override
    protected DefaultReloadableConfig doCreateReloadableConfig(Supplier<Config> configSupplier,
                                                               boolean reverseUpdateOrder) {
        def rc = DefaultReloadableConfig.builder()
                                        .refreshInterval(defaultRefreshInterval)
                                        .configSupplier(configSupplier)
                                        .reverseUpdateOrder(reverseUpdateOrder)
                                        .build();

        log.info("created RC: {}", rc)
        doPause()
        rc
    }

    private doPause() {
        Thread.sleep(20)
    }

    @Override
    protected DefaultReloadableConfig updateConfig(DefaultReloadableConfig rc, Map<String, Object> configMap) {
        super.updateConfig(rc, configMap)

        rc.refresh()
          .toCompletableFuture()
          .get(1, TimeUnit.SECONDS)

        doPause()
        rc
    }

    /**
     * Creates {@link ReloadableConfig} that uses default config stuff.
     * @return reloadable config
     */
    DefaultReloadableConfig createRc() {
        def rc = createReloadableConfig({
            log.info("fetching config")
            def cfg = defConfigSupplier.get()
            log.info("fetching done")
            cfg
        }, false)
        rc.getSync()
        rc
    }
}
