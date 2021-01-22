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

import com.github.tsc4j.testsupport.CloseableSupplier
import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import groovy.util.logging.Slf4j
import spock.lang.Specification
import spock.lang.Unroll

import java.util.function.Supplier

import static com.typesafe.config.ConfigFactory.empty

@Slf4j
@Unroll
class AggConfigSourceSpec extends Specification {
    static def defaultQuery = AbstractConfigSourceSpec.defaultConfigQuery

    def "should throw on invalid stuff"() {
        given:
        def builder = builder()

        when:
        def supplier = builderAction.call(builder).build()

        then:
        thrown(RuntimeException)
        supplier == null

        where:
        builderAction << [
            { it.fallbackSupplier(null) },
            { it.overrideSupplier(null) },
            { it.getSources(null) }
        ]
    }

    def "get() should throw immediately if one of suppliers throw on fetch"() {
        given: "setup suppliers"
        def firstCounter = 0
        def first = ["get": { firstCounter++; empty() }] as ConfigSource

        def secondCounter = 0
        def exception = new RuntimeException("haha")
        def second = ["get": { secondCounter++; throw exception }, "allowErrors": { false }] as ConfigSource

        def thirdCounter = 0
        def third = ["get": { thirdCounter++; ConfigFactory.defaultOverrides() }] as ConfigSource

        def overrideCounter = 0
        def overrideSupplier = { overrideCounter++; empty() } as Supplier<Config>

        and: "setup stack"
        def stack = builder().source(first)
                             .source(second)
                             .source(third)
                             .overrideSupplier(overrideSupplier)
                             .build()

        when: "ask for config"
        def config = stack.get(defaultQuery)

        then:
        def thrown = thrown(RuntimeException)
        thrown.getCause().is(exception)
        config == null

        firstCounter == 1
        secondCounter == 1
        thirdCounter == 0
        overrideCounter == 1
    }

    def "should not throw when default supplier returns no configuration"() {
        given:
        def configPath = "some.thing"
        def configValue = "foobar"
        def supplierConfig = empty()
            .withValue(configPath, ConfigValueFactory.fromAnyRef(configValue))

        and: "setup stack"
        def stack = builder().source({ supplierConfig } as ConfigSource)
                             .overrideSupplier({ null })
                             .build()

        when:
        def config = stack.get(defaultQuery)

        then:
        noExceptionThrown()
        !config.isEmpty()
        config.getString(configPath) == configValue
    }

    //@Ignore("TODO: fix this test.")
    def "get() should tolerate suppliers that return null value"() {
        given:
        def sourcePath = "/testcfg/stackable.conf"
        def configFile = new File(getClass().getResource(sourcePath).path)

        def userHome = System.getProperty("user.home")
        def foobar = "blahblah"
        def configPath = "some.value"

        and: "setup suppliers"
        def firstCounter = 0
        def first = {
            firstCounter++
            ConfigFactory.parseFile(configFile)
        } as ConfigSource

        def secondCounter = 0
        def second = { secondCounter++; null } as ConfigSource

        def thirdCounter = 0
        def third = {
            thirdCounter++
            empty()
                .withValue("foo.bar", ConfigValueFactory.fromAnyRef(foobar))
        } as ConfigSource

        and: "setup stack"
        def stack = builder().source(first)
                             .source(second)
                             .source(third)
                             .overrideSupplier({ ConfigFactory.load("testcfg/application.conf") })
                             .build()

        and: "set query"
        def query = ConfigQuery.builder()
                               .appName("someApp")
                               .envs(["a", "b"])
                               .datacenter("datacenter")
                               .build()

        when: "ask for config"
        def config = stack.get(query)
        log.info("got config: {}", config)
        def cfgValue = config.getString(configPath)
        log.info("config path {} resolves to: '{}'", configPath, cfgValue)

        then:
        noExceptionThrown()

        firstCounter == 1
        secondCounter == 1
        thirdCounter == 1

        !config.isEmpty()
        config.isResolved()

        cfgValue == "my home dir: ${userHome} - ${foobar}"
    }

    def "get() should throw if fallback config supplier throws"() {
        given: "setup stack"
        def exception = new RuntimeException("foo")
        def stack = builder().source({ empty() } as ConfigSource)
                             .fallbackSupplier({ throw exception })
                             .build()

        when:
        def config = stack.get(defaultQuery)

        then:
        def thrown = thrown(exception.getClass())
        thrown.is(exception)
        config == null
    }

    def "get(query) should throw if override config supplier throws"() {
        given: "setup stack"
        def exception = new RuntimeException("foo")
        def stack = builder().source({ empty() } as ConfigSource)
                             .overrideSupplier({ throw exception })
                             .build()

        when:
        def config = stack.get(defaultQuery)

        then:
        def thrown = thrown(exception.getClass())
        thrown.is(exception)
        config == null
    }

    def "get(query) should throw if config supplier throws"() {
        given: "setup stack"
        def exception = new RuntimeException("foo")
        def stack = builder().source({ throw exception } as ConfigSource)
                             .build()

        when:
        def config = stack.get(defaultQuery)

        then:
        def thrown = thrown(exception.getClass())
        thrown.is(exception)
        config == null
    }

    def "get() should throw if configuration cannot be resolved"() {
        given: "setup stack"
        def sourcePath = "/testcfg/stackable.conf"
        def file = new File(getClass().getResource(sourcePath).path)
        def stack = builder().source({ ConfigFactory.parseFile(file) } as ConfigSource)
                             .fallbackSupplier({ empty() })
                             .build()

        when:
        log.info("stack: {}", stack)
        def config = stack.get(defaultQuery)

        then:
        def thrown = thrown(ConfigException)
        thrown.getMessage().contains("user.home")
    }

    def "close() should properly close all suppliers if they implement Closeable"() {
        given: "define suppliers"
        boolean firstClosed = false
        def first = new CloseableSupplier(onClose: { firstClosed = true })

        def secondClosed = false
        def exception = new RuntimeException("haha")
        def second = new CloseableSupplier(onClose: { secondClosed = true; throw exception })

        def thirdClosed = false
        def third = new CloseableSupplier(onClose: { thirdClosed = true })

        def fourth = {} as ConfigSource

        def fallbackClosed = false
        def fallbackSupplier = new CloseableSupplier(onClose: {
            fallbackClosed = true; throw new RuntimeException("foo")
        })

        def overrideClosed = false
        def overrideSupplier = new CloseableSupplier(onClose: {
            overrideClosed = true; throw new RuntimeException("foo")
        })

        and: "create stack"
        def stack = AggConfigSource.builder()
                                   .sources([first, second, third, fourth])
                                   .fallbackSupplier(fallbackSupplier)
                                   .overrideSupplier(overrideSupplier)
                                   .build()

        when: "close the supplier"
        stack.close()

        then:
        noExceptionThrown()
        verifyAll {
            firstClosed == true
            secondClosed == true
            thirdClosed == true
            overrideClosed == true
            fallbackClosed == true
        }
    }

    def "fetchConfig(configSource, query) should tolerate fetch error and return empty optional"() {
        given:
        def configSource = Mock(ConfigSource)

        def source = AggConfigSource.builder().build()

        when:
        def configOpt = source.fetchConfig(configSource, defaultQuery)

        then:
        1 * configSource.get(defaultQuery) >> { throw new RuntimeException("foo") }
        1 * configSource.allowErrors() >> true

        noExceptionThrown()
        !configOpt.isPresent()
    }

    def "fetchConfig(configSource, query) should not tolerate fetch error"() {
        given:
        def exception = new RuntimeException("foo")
        def configSource = Mock(ConfigSource)
        def source = AggConfigSource.builder().build()

        when:
        def configOpt = source.fetchConfig(configSource, defaultQuery)

        then:
        1 * configSource.get(defaultQuery) >> { throw exception }
        configSource.allowErrors() >> false

        def thrown = thrown(RuntimeException)
        thrown.getCause().is(exception)
        configOpt == null
    }

    def "fetchConfigs() should return empty list for suppliers that all throw fetch exceptions and allow errors"() {
        given:
        def exception = new RuntimeException("foo")
        def configSourceA = Mock(ConfigSource)
        def configSourceB = Mock(ConfigSource)

        and:
        def source = AggConfigSource.builder()
                                    .source(configSourceA)
                                    .source(configSourceB)
                                    .build()

        when:
        def configs = source.fetchConfigs(defaultQuery)

        then:
        configSourceA.get() >> { throw exception }
        configSourceA.allowErrors() >> true
        configSourceB.get() >> { throw exception }
        configSourceB.allowErrors() >> true

        noExceptionThrown()
        configs.isEmpty()
    }

    def "fetchConfigs(query) should throw for source that throws and doesn't allow errors"() {
        given:
        def exception = new RuntimeException("foo")
        def configSourceA = Mock(ConfigSource)

        and:
        def source = AggConfigSource.builder()
                                    .source(configSourceA)
                                    .build()

        when:
        def configs = source.fetchConfigs(defaultQuery)

        then:
        configSourceA.get(defaultQuery) >> { throw exception }
        configSourceA.allowErrors() >> false

        def thrown = thrown(RuntimeException)
        thrown.getCause().is(exception)
        configs == null
    }

    def "fetchConfigs(query) should return expected result"() {
        given: "setup sources"
        def cfgA = empty().withValue("a", ConfigValueFactory.fromAnyRef(true))
        def configSourceA = Mock(ConfigSource)

        def exception = new RuntimeException("foo")
        def configSourceB = Mock(ConfigSource)

        def cfgC = empty().withValue("c", ConfigValueFactory.fromAnyRef(false))
        def configSourceC = Mock(ConfigSource)

        and: "setup stackable supplier"
        def source = AggConfigSource.builder()
                                    .source(configSourceA)
                                    .source(configSourceB)
                                    .source(configSourceC)
                                    .build()

        when:
        def configs = source.fetchConfigs(defaultQuery)

        then:
        1 * configSourceA.get(defaultQuery) >> cfgA
        1 * configSourceB.get(defaultQuery) >> { throw exception }
        1 * configSourceB.allowErrors() >> true
        1 * configSourceC.get(defaultQuery) >> cfgC

        configs.size() == 2
        configs.get(0).is(cfgA)
        configs.get(1).is(cfgC)
    }

    def "override supplier config should override all other suppliers"() {
        given:
        def overrideConfig = empty().withFallback(ConfigValueFactory.fromMap(["a": 42]))
        def fallbackConfig = empty().withFallback(ConfigValueFactory.fromMap(["a": 21]))
        def normalSupplierConfig = empty().withFallback(ConfigValueFactory.fromMap(["a": 10]))

        def supplier = AggConfigSource.builder()
                                      .source({ normalSupplierConfig } as ConfigSource)
                                      .overrideSupplier({ overrideConfig })
                                      .fallbackSupplier({ fallbackConfig })
                                      .build()

        when:
        def config = supplier.get(defaultQuery)

        then:
        config.getInt('a') == 42
    }

    def "fallback supplier config should provide defaults"() {
        given:
        def overrideConfig = empty()
        def fallbackConfig = empty().withFallback(ConfigValueFactory.fromMap(["a": 21]))
        def normalSupplierConfig = empty()

        def supplier = AggConfigSource.builder()
                                      .source({ normalSupplierConfig } as ConfigSource)
                                      .overrideSupplier({ overrideConfig })
                                      .fallbackSupplier({ fallbackConfig })
                                      .build()

        when:
        def config = supplier.get(defaultQuery)

        then:
        config.getInt('a') == 21
    }

    def "should fetch configuration in correct order and correctly construct final config"() {
        given: "setup first supplier"
        def userHome = System.getProperty('user.home').toString()
        def cfgMapA = [
            'top': ['a': 10, 'b': "bar", 'boolean': false, 'test': '-foo']
        ]
        def fetchTsA = 0
        def supplierA = {
            fetchTsA = System.currentTimeMillis()
            Thread.sleep(2)
            empty().withFallback(ConfigValueFactory.fromMap(cfgMapA))
        } as ConfigSource

        and: "setup second supplier"
        def cfgMapB = [
            'top': ['b': 'baz', 'boolean': 447, 'c': 'foobar']
        ]
        def fetchTsB = 0
        def supplierB = {
            fetchTsB = System.currentTimeMillis()
            Thread.sleep(2)
            empty().withFallback(ConfigValueFactory.fromMap(cfgMapB))
        } as ConfigSource

        and: "setup third supplier"
        def fetchTsC = 0
        def supplierC = Mock(ConfigSource)

        and: "setup default override supplier"
        def fetchTsOverride = 0
        def supplierOverride = {
            fetchTsOverride = System.currentTimeMillis()
            Thread.sleep(2)
            ConfigFactory.parseMap(["my.user.name": 'obviously_fake'])
        }

        and: "setup default fallback supplier"
        def fetchTsFallback = 0
        def supplierFallback = {
            fetchTsFallback = System.currentTimeMillis()
            Thread.sleep(2)
            def hocon = '{top.userHome: ${user.home}-blah}'
            ConfigFactory.parseString(hocon)
                         .withFallback(ConfigFactory.load())
        }

        and: "setup stack"
        def stack = AggConfigSource.builder()
                                   .source(supplierA)
                                   .source(supplierB)
                                   .source(supplierC)
                                   .overrideSupplier(supplierOverride)
                                   .fallbackSupplier(supplierFallback)
                                   .build()

        when: "fetch config"
        def config = stack.get(defaultQuery)
        // log.info("fetched config:\n{}", ReloadableConfigUtils.render(config, true))

        then:
        supplierC.get(defaultQuery) >> { fetchTsC = System.currentTimeMillis(); throw new RuntimeException("foo") }
        supplierC.allowErrors() >> true

        noExceptionThrown()
        config != null

        // fetch order should be correct
        fetchTsOverride > 0
        fetchTsA > fetchTsOverride
        fetchTsB > fetchTsA
        fetchTsC > fetchTsB
        fetchTsFallback >= fetchTsC

        // check values
        !config.isEmpty()
        config.isResolved()
        config.getInt('top.a') == 10
        config.getString('top.b') == 'baz'
        config.getInt('top.boolean') == 447
        config.getString('top.boolean') == "447"
        config.getString('top.c') == "foobar"
        config.getString('top.test') == "-foo"
        //config.getString('user.home') == userHome
        config.getString('my.user.name') == "obviously_fake"
    }

    AggConfigSource.AggConfigSourceBuilder builder() {
        AggConfigSource.builder()
    }
}
