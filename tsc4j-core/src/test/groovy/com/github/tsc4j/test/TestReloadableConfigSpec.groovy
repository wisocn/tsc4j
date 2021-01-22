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

package com.github.tsc4j.test

import com.github.tsc4j.core.ConfigSource
import com.github.tsc4j.core.Tsc4j
import com.github.tsc4j.core.Tsc4jException
import com.github.tsc4j.core.impl.AbstractReloadableConfigSpec
import com.github.tsc4j.core.impl.ConfigSupplier
import com.github.tsc4j.core.impl.Stopwatch
import com.github.tsc4j.testsupport.TestConstants
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueFactory
import spock.lang.Unroll

import java.nio.file.Paths
import java.util.function.Supplier

@Unroll
class TestReloadableConfigSpec extends AbstractReloadableConfigSpec<TestReloadableConfig> {
    static String configFileClasspath = "testcfg/application.conf"
    static String configFile = TestReloadableConfigSpec.class.getResource("/" + configFileClasspath).getFile()
    static def refConfig = Tsc4j.resolveConfig(ConfigFactory.parseResources(configFileClasspath))

    @Override
    protected TestReloadableConfig createReloadableConfig(boolean reverseUpdateOrder = false) {
        def rc = TestReloadableConfig.empty().reverseUpdateOrder(reverseUpdateOrder)
        log.info("created: {}, reverse: {}", rc, rc.isReverseUpdateOrder())
        rc
    }

    @Override
    protected TestReloadableConfig doCreateReloadableConfig(Supplier<Config> configSupplier, boolean reverseUpdateOrder) {
        TestReloadableConfig.fromSupplier(configSupplier).reverseUpdateOrder(reverseUpdateOrder)
    }

    @Override
    protected TestReloadableConfig updateConfig(TestReloadableConfig rc, Map<String, Object> configMap) {
        rc.set(configMap)
    }

    def "constructor should throw on null args"() {
        when:
        new TestReloadableConfig(null, null)

        then:
        thrown(NullPointerException)
    }

    def "fromConfig() should return instance that returns given config"() {
        when:
        def rc = TestReloadableConfig.fromConfig(refConfig)

        then:
        rc.isPresent()
        rc.getSync() == refConfig
        rc.get().toCompletableFuture().get() == refConfig
    }

    def "fromSupplier() with supplier that returns null configuration should not contain configuration"() {
        when:
        def called = false
        def rc = TestReloadableConfig.fromSupplier({ called = true; null })

        then:
        rc != null
        !rc.isPresent()
        called == true
    }

    def "fromSupplier() that throws should result in a empty reloadable config"() {
        given:
        def called = false
        def doThrow = new RuntimeException("kab00m")
        def supplier = { called = true; throw doThrow }

        def rc = TestReloadableConfig.fromSupplier(supplier)

        expect:
        rc != null
        !rc.isPresent()
        rc.get().toCompletableFuture().isCompletedExceptionally()

        when: "ask for config"
        rc.getSync()

        then:
        def ex = thrown(Tsc4jException)
        ex.getCause().is(doThrow)

        cleanup:
        log.info("thrown exception:", ex)
    }

    def "fromClasspath() should return instance that doesn't contain configuration if file on classpath isn't present"() {
        when:
        def rc = TestReloadableConfig.fromClasspath("/non-existent-config.conf")

        then:
        !rc.isPresent()
    }

    def "fromClasspath() should return instance that contain configuration if file on classpath is present"() {
        when:
        def rc = TestReloadableConfig.fromClasspath("/" + configFileClasspath)

        then:
        rc.isPresent()
        rc.getSync() == refConfig
    }

    def "fromFilename() should return instance without configuration if file: #filename"() {
        when:
        def rc = TestReloadableConfig.fromFilename(filename)

        if (rc.isPresent()) {
            log.info("config: {}", rc.getSync())
        }

        then:
        !rc.isPresent()

        where:
        filename << ['/totally-non-existent.conf', '/etc/shadow', '/']
    }

    def "fromFileName(filename) should construct expected rc"() {
        when:
        def rc = TestReloadableConfig.fromFilename(configFile)

        then:
        rc.isPresent()
        rc.getSync() == refConfig
    }

    def "fromFile(filename) should construct expected rc"() {
        when:
        def rc = TestReloadableConfig.fromFile(new File(configFile))

        then:
        rc.isPresent()
        rc.getSync() == refConfig
    }

    def "fromFile() should construct expected rc"() {
        given:
        def filename = getClass().getResource("/" + configFileClasspath).getFile()

        when:
        def rc = TestReloadableConfig.fromFile(new File(filename))

        then:
        rc.isPresent()
        rc.getSync() == refConfig
    }

    def "fromPath(path) should construct expected rc"() {
        when:
        def rc = TestReloadableConfig.fromPath(Paths.get(configFile.toString()))

        then:
        rc.isPresent()
        rc.getSync().isResolved()
        rc.getSync() == refConfig
    }

    def "fromMap(map) should construct expected rc"() {
        given:
        def map = [foo: 'bar']

        when:
        def rc = TestReloadableConfig.fromMap(map)

        then:
        rc.isPresent()
        rc.getSync().root().unwrapped() == map
    }

    def "fromSupplier(supplier) should construct expected rc"() {
        when:
        def rc = TestReloadableConfig.fromSupplier({ refConfig })

        then:
        rc.isPresent()
        rc.getSync().is(refConfig)
    }

    def "setPreviousConfig() should apply previous config"() {
        given:
        def origPath = 'foo'
        def origMap = [(origPath): 'bar']
        def rc = TestReloadableConfig.fromMap(origMap)

        expect:
        def origConfig = rc.getSync()
        origConfig.root().size() == 1
        origConfig.getString('foo') == origMap[origPath]

        when: "try to set previous config"
        rc.setPreviousConfig()

        then: "there should not be any previous config"
        thrown(IllegalStateException)

        when: "set new config"
        def newPath = 'some.path'
        def newMap = [a: 'b']
        rc.set(newPath, newMap)

        then:
        def newConfig = rc.getSync()

        // ensure that current config is correctly applied
        newConfig.root().size() == 2
        newConfig.getString(origPath) == origMap[origPath]
        newConfig.getString(newPath + ".a") == newMap['a']

        when: "roll back to previous config"
        def r = rc.setPreviousConfig()

        then: "original config should be restored"
        r.is(rc)
        rc.getSync().is(origConfig)

        when: "ask for previous config again"
        rc.setPreviousConfig()

        then: "we should get foo"
        rc.getSync().is(newConfig)
    }

    def "setOriginalConfig() should set first fetched config"() {
        given:
        def origPath = 'foo'
        def origMap = [(origPath): 'bar']
        def rc = TestReloadableConfig.fromMap(origMap)

        when:
        def origConfig = rc.getSync()

        then:
        origConfig.root().size() == 1
        origConfig.getString(origPath) == origMap[origPath]

        when: "set new configs"
        rc.set("x.y", 10)
        rc.set("x.z", 12)

        then:
        def newConfig = rc.getSync()
        newConfig != origConfig
        newConfig.root().size() == 2
        newConfig.getString(origPath) == origMap[origPath]
        newConfig.getInt("x.y") == 10
        newConfig.getInt("x.z") == 12

        when: "restore original config"
        def r = rc.setOriginalConfig()

        then:
        r.is(rc)
        rc.getSync().is(origConfig)
    }

    def "fromFactory() should return expected instance"() {
        when:
        def rc = TestReloadableConfig.fromFactory()

        then:
        rc.isPresent()
        def config = rc.getSync()

        assertFactoryConfig(config)
    }

    def "fromFactory(appname) should return expected instance"() {
        when:
        def rc = TestReloadableConfig.fromFactory("my-cool-app")

        then:
        rc.isPresent()
        def config = rc.getSync()

        assertFactoryConfig(config)
    }

    def "fromFactory(appname, env1, env2) should return expected instance"() {
        when:
        def rc = TestReloadableConfig.fromFactory("my-cool-app", "env1", "env2")

        then:
        rc.isPresent()
        def config = rc.getSync()

        assertFactoryConfig(config)
    }

    def "fromFactory(appname, envs) should return expected instance"() {
        when:
        def rc = TestReloadableConfig.fromFactory("my-cool-app", ["env1", "env2"])

        then:
        rc.isPresent()
        def config = rc.getSync()

        assertFactoryConfig(config)
    }

    def assertFactoryConfig(Config cfg) {
        assert !cfg.isEmpty()
        assert cfg.isResolved()

        assert cfg.getString("myapp.internal.a") == 'foo'
        true
    }

    def 'fromFactory'() {
        given:
        def appName = "blah"
        def envs = ["foo", "bar"]

        and:
        def numTimes = 1

        when:
        def results = numTimes.times {
            def sw = new Stopwatch()
            def rc = TestReloadableConfig.fromFactory(appName, envs)
            log.info("created test rc in: {}", sw)
            rc.close()
            rc
        }

        then:
        true
    }

    def "empty() should return rc with empty config"() {
        when:
        def rc = TestReloadableConfig.empty()

        then:
        rc.isPresent()
        rc.getSync().isEmpty()
    }

    def "created reloadables should contain expected values"() {
        given:
        def rc = TestReloadableConfig.fromConfig(refConfig)

        and: "create reloadables"
        def invoked3 = null
        def invoked4 = null
        def r1 = rc.register("something.var1", String)
        def r2 = rc.register("something.var2", Integer)
        def r3 = rc.register("something.var3", String)
                   .ifPresentAndRegister({ invoked3 = it })
        def r4 = rc.register("something.var4", String)
                   .ifPresentAndRegister({ invoked4 = it })

        expect:
        rc.isPresent()
        rc.getSync().is(refConfig)

        r1.isPresent()
        r2.isPresent()
        r3.isPresent()
        !r4.isPresent()

        // inspect vars
        r1.get().startsWith('application ref: ')
        r2.get() == 11
        r3.get() == 'fooblah'

        r3.get() == 'fooblah'
        invoked3 == 'fooblah'
        invoked4 == null
    }

    def "clear() should clear assigned config and created reloadables"() {
        given:
        def rc = TestReloadableConfig.fromConfig(refConfig)

        and: "create reloadables"
        def reloadables = [
            rc.register("something.var1", String),
            rc.register("something.var2", Integer),
            rc.register("something.var3", String),
        ]

        expect:
        rc.isPresent()
        rc.getSync().is(refConfig)
        reloadables.each { assert it.isPresent() }

        when: "clear rc"
        def res = rc.clear()

        then:
        res.is(rc)
        rc.getSync().isEmpty()

        // reloadables should be empty now
        reloadables.each { assert !it.isPresent() }
    }

    def "set() should assign new config"() {
        given:
        def cfgMap = [a: "foo", b: 10]
        def rc = TestReloadableConfig.empty()

        expect:
        rc.isPresent()
        rc.getNumUpdates() == 1
        rc.getSync().isEmpty()

        when: "assign update"
        def result = rc.set(cfgMap)
        log.info("after first update: {}", rc)

        then:
        result.is(rc)
        rc.isPresent()
        rc.getNumUpdates() == 2
        rc.getSync().root().unwrapped() == cfgMap

        when: "assign the same config 10 times"
        10.times { rc.set(cfgMap) }
        log.info("after second update: {}", rc)

        then: "number of updates should not change"
        result.is(rc)
        rc.isPresent()
        rc.getNumUpdates() == 2
        rc.getSync().root().unwrapped() == cfgMap

        when: "do a refresh"
        result = rc.refresh()
        log.info("after fresh: {}", rc)

        then: "it should return original empty config from supplier, number of updates should be incremented"
        result.isDone()
        result.get() == ConfigFactory.empty()
        rc.isPresent()
        rc.getSync().isEmpty()
        rc.getNumUpdates() == 3

        when: "try setting actual config instance"
        def configMap = [foo: 'bar']
        result = rc.set(ConfigFactory.parseMap(configMap))

        then:
        result.is(rc)

        rc.isPresent()
        rc.getSync().root().unwrapped() == configMap
        rc.getNumUpdates() == 4
    }

    def "set(path, value) should update specific key"() {
        given:
        def rc = TestReloadableConfig.empty()

        and: "setup update config"
        def path = "foo.bar"
        def cfgMap = [a: 'b']
        def partialConfig = ConfigFactory.parseMap(cfgMap)

        and: "setup reloadable that should be notified about changes"
        def numUpdates = 0
        def updatedWith = null
        def reloadable = rc.register(path, ConfigValue).register({
            numUpdates++
            updatedWith = it
            log.info("updated #${numUpdates} times; got {} -> {}", it.getClass().getName(), it)
        })

        expect:
        rc.isPresent()
        rc.getSync().isEmpty()

        when: "set config instance"
        log.info("setting config instance: {}", partialConfig)
        def res = rc.set(path, partialConfig)

        then:
        res.is(rc)
        rc.isPresent()
        !rc.getSync().isEmpty()

        rc.getSync().getConfig(path) == partialConfig

        numUpdates == 1
        updatedWith == partialConfig.root()

        when:
        log.info("setting map instance: {}", cfgMap)
        res = rc.clear().set(path, cfgMap)

        then:
        res.is(rc)
        rc.getSync().getConfig(path) == partialConfig

        numUpdates == 3
        updatedWith == partialConfig.root()

        when:
        log.info("setting configValue instance: {}", partialConfig.root())
        res = rc.clear().set(path, partialConfig.root())

        numUpdates == 5
        updatedWith == partialConfig.root()

        then:
        res.is(rc)
        rc.getSync().getConfig(path) == partialConfig

        when:
        log.info("setting integer instance: {}", 10)
        res = rc.clear().set(path, 10)

        then:
        res.is(rc)
        rc.getSync().getInt(path) == 10
        numUpdates == 7
        updatedWith == ConfigValueFactory.fromAnyRef(10)
    }

    def "remove() should remove given path"() {
        given:
        def path = "bar"
        def map = [
            foo: "bar",
            bar: [
                a: "b",
                c: "d"
            ]
        ]
        def config = ConfigFactory.parseMap(map)

        and: "setup reloadable config"
        def rc = TestReloadableConfig.fromConfig(config)

        and: "setup reloadable"
        def numUpdates = 0
        def updatedWith = "f000"
        def reloadable = rc.register(path, ConfigValue).register({
            numUpdates++
            updatedWith = it
        })

        expect:
        rc.getSync().hasPath(path)
        rc.getSync().hasPath('bar')
        rc.getSync().hasPath('bar.a')
        rc.getSync().hasPath('bar.c')

        reloadable.isPresent()
        reloadable.get().unwrapped() == map.get(path)

        numUpdates == 0

        when: "remove path"
        def res = rc.remove(path)

        then:
        res.is(rc)

        rc.getSync().getString("foo") == "bar"
        !rc.getSync().hasPath(path)

        !reloadable.isPresent()
        numUpdates == 1
        updatedWith == null
    }

    def "close should close supplier and invoke onClose"() {
        given:
        def numOnClose = 0
        def onClose = { numOnClose++ }

        def source = Mock(ConfigSource)
        source.get(_) >> ConfigFactory.empty()

        def supplier = new ConfigSupplier(source, TestConstants.defaultConfigQuery)

        and: "setup rc"
        def rc = new TestReloadableConfig(supplier, onClose)

        when:
        rc.close()

        then:
        1 * source.close()
        numOnClose == 1
    }
}
