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

import com.github.tsc4j.core.impl.ClasspathConfigSource
import com.github.tsc4j.core.impl.CliConfigSource
import com.github.tsc4j.core.impl.SimpleTsc4jCache
import com.github.tsc4j.core.impl.Stopwatch
import com.github.tsc4j.testsupport.TestUtils
import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicInteger

import static com.github.tsc4j.testsupport.TestConstants.TEST_CFG_INVALID_STR
import static com.github.tsc4j.testsupport.TestConstants.TEST_CFG_STRING

@Unroll
class Tsc4jImplUtilsSpec extends Specification {
    private Logger log = LoggerFactory.getLogger(getClass())

    @Shared
    def runnableCalled = false

    def setup() {
        runnableCalled = false
        TestUtils.cleanupVars()
    }

    def cleanupSpec() {
        TestUtils.cleanupVars()
    }

    def "checkExecutor should succeed on valid executor"() {
        when:
        def res = Tsc4jImplUtils.checkExecutor(executor)

        then:
        noExceptionThrown()
        res.is(executor)

        where:
        executor << [
            createExecutor(ExecutorService),
            createExecutor(ScheduledExecutorService),
        ]
    }

    def "checkExecutor(#executor) should throw on bad executor"() {
        when:
        def res = Tsc4jImplUtils.checkExecutor(executor)

        then:
        thrown(RuntimeException)
        res == null

        where:
        executor << [
            null,
            createExecutor(ExecutorService, { it.isShutdown() >> true }),
            createExecutor(ExecutorService, { it.isTerminated() >> true }),
            createExecutor(ExecutorService, { it.isShutdown() >> true; it.isTerminated() >> true }),
        ]
    }

    def createExecutor(Class<? extends ExecutorService> clazz, Closure closure = {}) {
        def executor = Mock(clazz)
        closure.call(executor)
        executor
    }

    def "safeRunnable() should throw on null input"() {
        when:
        def res = Tsc4jImplUtils.safeRunnable(null)

        then:
        thrown(NullPointerException)
        res == null
    }

    def "safeRunnable() should create really runnable that doesn't throw in case of underlying exception"() {
        when:
        runnableCalled = false
        Tsc4jImplUtils.safeRunnable(runnable).run()

        then:
        noExceptionThrown()
        runnableCalled == true // verify that original runnable has been invoked

        where:
        runnable << [
            { runnableCalled = true; throw new RuntimeException("thou shall not pass") },
            { runnableCalled = true; def x = 5 / 0 },
            { runnableCalled = true }
        ]
    }

    def "close() should close the closeable"() {
        given:
        def closed = false
        def closeable = { closed = true } as Closeable

        when:
        def res = Tsc4jImplUtils.close(closeable)

        then:
        res == true
        closed == true
    }

    def "close should return false for failures"() {
        when:
        def res = Tsc4jImplUtils.close(closeable)

        then:
        noExceptionThrown()
        res == false

        where:
        closeable << [null, { throw new RuntimeException("boo") } as Closeable]
    }

    def "close(closeable, log) should log errors"() {
        given:
        def logger = Mock(Logger)
        def closed = false
        def exception = new RuntimeException("boo")
        def closeable = { closed = true; throw exception } as Closeable

        when:
        def res = Tsc4jImplUtils.close(closeable, logger)

        then:
        noExceptionThrown()
        res == false

        1 * logger.error(_, _, _, exception)
    }

    def "toStringList() should return expected result"() {
        expect:
        Tsc4jImplUtils.toStringList((Object) input) == expected

        where:
        input              | expected
        []                 | []
        null               | []
        "a;b,b;c,  d ,d"   | ["a", "b", "c", "d"] // basic string
        ["a;b,b;c", "d,d"] | ["a", "b", "c", "d"] // list of strings
    }

    def "openFromFilesystem() should return optional with input stream for existing file"() {
        given:
        def tmpFile = Files.createTempFile(".prefix", ".suffix")

        when:
        def isOpt = Tsc4jImplUtils.openFromFilesystem(tmpFile.toString())

        then:
        isOpt.isPresent()

        cleanup:
        isOpt?.ifPresent({ it.close() })
        Files.deleteIfExists(tmpFile)
    }

    def "openFromFilesystem() should return empty optional for non-existing file"() {
        when:
        def isOpt = Tsc4jImplUtils.openFromFilesystem("/non-existing-file-" + Math.random())

        then:
        !isOpt.isPresent()
    }

    @Requires({ os.isLinux() })
    def "openFromFilesystem() should throw exception if file cannot be opened"() {
        when:
        def isOpt = Tsc4jImplUtils.openFromFilesystem(filename)

        then:
        def thrown = thrown(IOException)
        thrown.toString().toLowerCase().contains("accessdenied")
        isOpt == null

        where:
        filename << ["/etc/shadow"]
    }

    def "openFromClasspath should return non-empty optional for existing resource"() {
        when:
        def isOpt = Tsc4jImplUtils.openFromClassPath(filename)

        then:
        isOpt.isPresent()

        cleanup:
        isOpt?.ifPresent({ it.close() })

        where:
        filename << ["logback-test.xml", "/logback-test.xml", "///logback-test.xml"]
    }

    def "openFromFilesystemOrClasspath should return non-empty optional"() {
        when:
        def isOpt = Tsc4jImplUtils.openFromFilesystemOrClassPath(filename)

        then:
        isOpt.isPresent()

        cleanup:
        isOpt?.ifPresent({ it.close() })

        where:
        filename << ["logback-test.xml", "/logback-test.xml", "///logback-test.xml"]
    }

    def "openFromFilesystemOrClasspath should return empty optional"() {
        when:
        def isOpt = Tsc4jImplUtils.openFromFilesystemOrClassPath("/non-existing-file-" + Math.random())

        then:
        !isOpt.isPresent()

        cleanup:
        isOpt?.ifPresent({ it.close() })
    }

    def "sanitizeAppName('#name') should throw"() {
        when:
        def res = Tsc4jImplUtils.sanitizeAppName(name)

        then:
        thrown(RuntimeException)
        res == null

        where:
        name << [null, "", " ", "foo×", "foo|"]
    }

    def "sanitizeAppName('#name') should return trimmed result"() {
        when:
        def res = Tsc4jImplUtils.sanitizeAppName(name)

        then:
        res == name.trim()

        where:
        name << ["a", "foo", "foo-bar", "foo_bar", "0foo_-bar9", " foobar "]
    }

    def "sanitizeEnvs() should throw"() {
        when:
        def res = Tsc4jImplUtils.sanitizeEnvs(envs)

        then:
        thrown(IllegalArgumentException)
        res == null

        where:
        envs << [
            [" Đ"],
            ["a", "×"],
        ]
    }

    def "sanitizeEnvs() should return expected result"() {
        when:
        def res = Tsc4jImplUtils.sanitizeEnvs(envs)

        then:
        res == expected

        where:
        envs                               | expected
        [" d", "d", "x"]                   | ["d", "x"]
        [" ", "d", "x", " x", "x ", " x "] | ["d", "x"]
    }

    def "toRuntimeException should return expected result"() {
        when:
        def result = Tsc4jImplUtils.toRuntimeException(exception)

        then:
        result instanceof RuntimeException
        result.getMessage() == exception.getMessage()

        where:
        exception << [
            new IllegalArgumentException("foo"),
            new RuntimeException("foo"),
            new IOException("foo"),
            new IOException()
        ]
    }

    @Unroll
    def "readConfig() should correctly parse input"() {
        given:
        def origin = "someOriginName"

        when:
        def config = Tsc4jImplUtils.readConfig(input, origin)
        log.info("parsed: {}", config)

        then:
        !config.isEmpty()
        config.getInt('a') == 42
        config.getBoolean('b') == true

        where:
        input << [
            TEST_CFG_STRING.getBytes(StandardCharsets.UTF_8),
            TEST_CFG_STRING,
            new ByteArrayInputStream(TEST_CFG_STRING.getBytes(StandardCharsets.UTF_8)),
            new InputStreamReader(new ByteArrayInputStream(TEST_CFG_STRING.getBytes(StandardCharsets.UTF_8)))
        ]
    }

    @Unroll
    def "readConfig() should correctly handle parse errors"() {
        given:
        def origin = "someOriginName"

        when:
        def config = Tsc4jImplUtils.readConfig(input, origin)

        then:
        def exception = thrown(ConfigException)
        config == null

        exception.origin().description().contains(origin)
        exception.origin().lineNumber() == 7
        exception.getMessage().contains(': 7: expecting a close parentheses ')

        where:
        input << [
            TEST_CFG_INVALID_STR.getBytes(StandardCharsets.UTF_8),
            TEST_CFG_INVALID_STR,
            new ByteArrayInputStream(TEST_CFG_INVALID_STR.getBytes(StandardCharsets.UTF_8)),
            new InputStreamReader(new ByteArrayInputStream(TEST_CFG_INVALID_STR.getBytes(StandardCharsets.UTF_8)))
        ]
    }

    def "scanConfig() should perform configuration scan over all keys"() {
        given:
        def config = ConfigFactory.parseResources("testcfg/transformer-test.conf")
                                  .withFallback(ConfigFactory.systemProperties())
                                  .resolve()
        def numEntries = 0
        def numLists = 0
        def keys = []
        def configKeySet = config.entrySet().collect({ it.getKey() }) as Set

        when:
        Tsc4jImplUtils.scanConfig(config, { key, value ->
            keys.add(key)
            numEntries++
            if (value.valueType() == ConfigValueType.LIST) {
                numLists++
            }
            log.info("got: {} ({}): {}", key, value.valueType(), value)
        })

        then:
        numEntries > 50
        numLists == 1
        keys.size() == configKeySet.size()
        keys as Set == configKeySet
    }

    def "scanConfigValue() should perform configuration scan over all keys and trough all elements of lists/objects"() {
        given:
        def config = ConfigFactory.parseResources("testcfg/transformer-test.conf")
                                  .withFallback(ConfigFactory.systemProperties())
                                  .resolve()
        def resultMap = [:]
        def configKeySet = config.entrySet().collect({ it.getKey() }) as Set

        when:
        Tsc4jImplUtils.scanConfigValue("", config.root(), { key, value ->
            resultMap.computeIfAbsent(key, { new AtomicInteger() }).incrementAndGet()
            log.info("got: {} ({}): {}", key, value.valueType(), value)
        })

        then:
        resultMap.keySet() == configKeySet
        resultMap['test.bean.aInt'].get() == 1
        resultMap['test.bean.aList'].get() == 4
    }

    def "defaultExecutor() should return singleton"() {
        given:
        def executors = (1..10).collect { Tsc4jImplUtils.defaultExecutor() }
        def first = executors.first()

        expect:
        !first.isTerminated()
        !first.isShutdown()

        executors.every { it.is(first) }
    }

    def "threadFactory() should create a thread with correct name"() {
        given:
        def poolName = "somePoolName"
        def runnable = { Thread.sleep(10_000) } as Runnable
        def factory = Tsc4jImplUtils.createThreadFactory(poolName)

        when:
        def thread = factory.newThread(runnable)

        then:
        thread != null
        thread.getName() == "tsc4j-${poolName}-1-1"
        thread.isDaemon()
        !thread.isAlive()

        when: "create some new threads"
        def threads = (1..5).collect({ factory.newThread(runnable) })

        then:
        threads.eachWithIndex { thr, idx ->
            assert thr.getName() == "tsc4j-${poolName}-1-" + (idx + 2)
            assert thread.isDaemon()
            assert !thr.isAlive()
        }
    }

    def "parallelCall() should return results in correct order"() {
        given:
        def expectedResults = ["a", "b", null, "c"]
        def callables = expectedResults.collect({
            def result = it
            def delay = (int) (Math.random() * (double) 50)
            return { Thread.sleep(delay); result } as Callable<String>
        })

        when:
        def results = Tsc4jImplUtils.parallelCall(callables)

        then:
        results == expectedResults
    }

    def "TestUtils.setEnvVar() should work as expected"() {
        given:
        def name = "SOME_ENV_VAR"
        def value = "SOME_VALUE"

        def backup = TestUtils.envBackup()

        expect:
        System.getenv(name) == null

        when:
        TestUtils.setEnvVar(name, value)

        then:
        System.getenv(name) == value

        cleanup: "restore system environment"
        TestUtils.envRestore(backup)
    }

    def "isEnabledConfig(#map) should return: #expected"() {
        given:
        def config = ConfigFactory.parseMap(map)

        expect:
        Tsc4jImplUtils.isEnabledConfig(config, []) == expected

        where:
        map                | expected
        [:]                | true
        [foo: "bar"]       | true

        [enabled: "false"] | false
        [enabled: false]   | false

        [enabled: "true"]  | true
        [enabled: true]    | true
    }

    def "isEnabledConfig(#map, [a,b,c]) should return #expected"() {
        given:
        def appEnvs = ['a', 'b', 'c']
        def config = ConfigFactory.parseMap(map)

        expect:
        Tsc4jImplUtils.isEnabledConfig(config, appEnvs) == expected

        where:
        map                                                                    | expected
        ['if-any-enabled-env': []]                                             | true
        ['if-any-enabled-env': ['']]                                           | true
        ['if-any-enabled-env': [' ']]                                          | true

        ['if-any-enabled-env': ['a']]                                          | true
        ['if-any-enabled-env': [' a ']]                                        | true
        ['if-any-enabled-env': ['a', 'a', 'b ']]                               | true
        ['if-any-enabled-env': ['a', 'a', 'b ', 'c']]                          | true
        ['if-any-enabled-env': ['a', 'b', 'c', 'd']]                           | true
        ['if-any-enabled-env': ['d']]                                          | false
        ['if-any-enabled-env': ['A']]                                          | false

        ['if-all-enabled-envs': ['a']]                                         | true
        ['if-all-enabled-envs': ['a']]                                         | true
        ['if-all-enabled-envs': ['a', 'b']]                                    | true
        ['if-all-enabled-envs': ['a', 'c', 'b',]]                              | true
        ['if-all-enabled-envs': ['a', 'c', 'c',]]                              | true
        ['if-all-enabled-envs': ['a', 'b', 'd',]]                              | false

        ['if-all-enabled-envs': ['a', 'b', 'd',], 'if-any-enabled-env': ['a']] | false
    }

    @RestoreSystemProperties
    def "propValue(#name) should return '#expected'"() {
        given:
        def envVarName = "FOO_BAR_BAZ"
        def envVarVal = "something"

        def sysPropName = "my.prop"
        def sysPropValue = "my.value"

        def envBackup = TestUtils.envBackup()
        def propsBackup = TestUtils.systemPropsBackup()

        expect:
        System.getenv(envVarName) == null
        System.getProperty(sysPropName) == null

        when:
        TestUtils.setEnvVar(envVarName, envVarVal)
        System.setProperty(sysPropName, sysPropValue)
        TestUtils.setEnvVar("MY_PROP", "THIS_IS_INSANE") // set the environment var with different value than props

        then:
        System.getenv(envVarName) == envVarVal
        System.getenv("MY_PROP") == "THIS_IS_INSANE"
        System.getProperty(sysPropName) == sysPropValue

        when: "now ask for property value"
        def valueOpt = Tsc4jImplUtils.propValue(name)

        then:
        if (expected) {
            assert valueOpt.isPresent()
            assert valueOpt.get() == expected
        } else {
            assert !valueOpt.isPresent()
        }

        cleanup:
        TestUtils.envRestore(envBackup)
        TestUtils.systemPropsRestore(propsBackup)

        where:
        name          | expected
        "user.home"   | System.getProperty("user.home")
        "foo.bar"     | null
        "foo.bar.baz" | "something" // should be read from environment variable
        "FOO_BAR_BAZ" | "something" // should be read from environment variable
        "my.prop"     | "my.value"  // should prefer system property over the environment value
    }

    def "parallelCall() should throw if any callable throws"() {
        given:
        def exception = new IOException("foo")
        def expectedResults = ["a", "b", null, "c"]
        def callables = expectedResults.collect({
            def result = it
            def delay = (int) (Math.random() * (double) 50)
            def doThrow = true
            return {
                Thread.sleep(delay)
                if (doThrow) {
                    throw exception
                }
                result
            } as Callable<String>
        })

        when:
        def results = Tsc4jImplUtils.parallelCall(callables)

        then:
        def ex = thrown(IOException)
        ex.is(exception)
        results == null
    }

    def "toCamelCase('#str') should return '#expected'"() {
        expect:
        Tsc4jImplUtils.toCamelCase(str) == expected

        where:
        str                         | expected
        ''                          | ''
        'a'                         | 'a'
        'A'                         | 'A'
        'AB'                        | 'AB'
        'a-b'                       | 'aB'
        'some-great-thing'          | 'someGreatThing'
        'some_great-thing'          | 'someGreatThing'
        'some_great_thing'          | 'someGreatThing'
        'some_grEAT_thing'          | 'someGreatThing'
        'SOME_GREAT_THIng'          | 'someGreatThing'
        'SOME__ __- - GREAT-_THIng' | 'someGreatThing'
        'someGreatThing'            | 'someGreatThing'
    }

    def "configVal(#key) should return expected result"() {
        given:
        def value = "bang"
        def cfgMap = [
            'foo-bar'  : value,
            'fooBar'   : value,
            'barBaz'   : value,
            'foo_bar'  : value,
            'fooBarBaz': value
        ]
        def config = ConfigFactory.parseMap(cfgMap)

        when:
        def opt = Tsc4jImplUtils.configVal(config, key, { cfg, k -> cfg.getString(k) })

        then:
        opt.isPresent() == expected
        if (!expected) {
            return
        }

        when:
        def received = opt.get()

        then:
        received == value

        where:
        key            | expected
        "foo-bar"      | true
        "fooBar"       | true
        'bar-baz'      | true
        'barBaz'       | true
        'foo-bar-baz'  | true
        "non-existent" | false
    }

    def "createTransformer() should throw in case of non-existing implementation"() {
        given:
        def config = mapConfig(cfgMap)

        when:
        Tsc4jImplUtils.createTransformer(config)

        then:
        thrown(RuntimeException)

        where:
        cfgMap << [
            [impl: "foobar"],
        ]
    }

    def "createTransformer() should throw in case of bad settings"() {
        given:
        def config = mapConfig(cfgMap)

        when:
        Tsc4jImplUtils.createTransformer(config)

        then:
        def exception = thrown(RuntimeException)

        cleanup:
        log.info("exception: ", exception)

        where:
        cfgMap << [
            [impl: "java.lang.String"],
            [impl: "com.github.tsc4j.testsupport.MyTransformer"],
            [impl: "com.github.tsc4j.testsupport.MyTransformer", someValue: -3],
            [impl: "com.github.tsc4j.testsupport.MyTransformer", someValue: 101],
        ]
    }

//    @PendingFeature
//    def "createTransformer() should return non-empty optional"() {
//        given:
//        def config = mapConfig(cfgMap)
//
//        when:
//        def transformerOpt = Tsc4jImplUtils.createTransformer(config)
//
//        then:
//        transformerOpt.isPresent()
//
//        when:
//        def transformer = transformerOpt.get()
//
//        then:
//        transformer instanceof Rot13ConfigTransformer
//
//        transformer.allowErrors() == true
//        transformer.getName() == "foo"
//
//        where:
//        cfgMap << [
//            [impl: "rot13", enabled: true, name: "foo", allowErrors: true],
//            [impl: "rot13", name: "foo", allowErrors: true],
//            [impl: "Rot13", name: "foo", allowErrors: true],
//            [impl: "Rot13ConfigTransformer", name: "foo", allowErrors: true],
//            [impl: "com.github.tsc4j.core.impl.Rot13ConfigTransformer", name: "foo", allowErrors: true],
//        ]
//    }

    def "createSource() should throw in case of non-existing implementation"() {
        given:
        def config = mapConfig(cfgMap)

        when:
        Tsc4jImplUtils.createConfigSource(config)

        then:
        thrown(RuntimeException)

        where:
        cfgMap << [
            [impl: "foobar"],
        ]
    }

    def "createSource() should throw in case of bad settings"() {
        given:
        def config = mapConfig(cfgMap)

        when:
        Tsc4jImplUtils.createConfigSource(config)

        then:
        def exception = thrown(RuntimeException)

        cleanup:
        log.info("exception: ", exception)

        where:
        cfgMap << [
            [impl: "java.lang.String"],
            [impl: "BadConfigSource1"],
            [impl: "BadConfigSource2"],
            [impl: "BadConfigSource3"],
            [impl: "BadConfigSource4"],
            [impl: "BadConfigSource5"],
            [impl: "com.github.tsc4j.testsupport.MyTransformer", someValue: -3],
            [impl: "com.github.tsc4j.testsupport.MyTransformer", someValue: 101],
        ]
    }

    Config mapConfig(Map m) {
        ConfigFactory.parseMap(m)
    }

    def "createSource() should initialize source"() {
        given:
        def pathList = ["/a", "b", "/c/d"]
        def map = [impl: "classpath", paths: pathList, allowErrors: true]
        def config = mapConfig(map)

        when:
        def sourceOpt = Tsc4jImplUtils.createConfigSource(config, 1)

        then:
        sourceOpt.isPresent()

        when:
        def source = sourceOpt.get()

        then:
        source instanceof ClasspathConfigSource
        source.getPaths() == pathList
        source.allowErrors() == true
    }

    def "createSource() should create classpath config source with default paths if paths are omitted from config"() {
        def map = [impl: "classpath"]
        def config = mapConfig(map)

        when:
        def sourceOpt = Tsc4jImplUtils.createConfigSource(config, 1)

        then:
        sourceOpt.isPresent()

        when:
        def source = sourceOpt.get()

        then:
        source instanceof ClasspathConfigSource
        source.getPaths() == ClasspathConfigSource.DEFAULT_CLASSPATH_PATHS
        source.allowErrors() == false
    }

    def "aggConfigSource() should add cli config source if requested by bootstrap config"() {
        given:
        def empty = ConfigFactory.empty()
        def config = Tsc4jConfig.builder().cliEnabled(flag).build()
        def appEnvs = []

        when:
        def source = Tsc4jImplUtils.aggConfigSource(config, appEnvs, { empty }, { empty })

        then:
        def sources = source.sources

        sources[0] instanceof ClasspathConfigSource
        if (flag) {
            assert sources.size() == 2
            assert sources[1] instanceof CliConfigSource
        } else {
            assert sources.size() == 1
        }

        where:
        flag << [true, false]
    }

    def "discoverAppName(#hint) should produce expected result"() {
        when:
        def appName = Tsc4jImplUtils.discoverAppName(hint)

        then:
        true
        where:
        hint | expected
        "a"  | "b"
    }

    def "loadBootstrapConfig(filename) should return valid bootstrap config even if config contains system props"() {
        given:
        def classpathName = "test-configs/bootstrap/tsc4j-unresolved-1.conf"
        def filename = getClass().getResource("/" + classpathName).getFile()

        log.info("config classpath  name: {}", classpathName)
        log.info("config filesystem name: {}", filename)

        when: "load it by classpath name"
        def config = Tsc4jImplUtils.loadBootstrapConfig(classpathName)

        then:
        assertUnresolvedBootstrapConfig(config)

        when: "load it from filesystem using filename"
        config = Tsc4jImplUtils.loadBootstrapConfig(filename)

        then:
        assertUnresolvedBootstrapConfig(config)

        when:
        config = Tsc4jImplUtils.loadBootstrapConfigFromFile(filename).get()

        then:
        assertUnresolvedBootstrapConfig(config)
    }

    def assertUnresolvedBootstrapConfig(Tsc4jConfig config) {
        def homeDir = System.getProperty("user.home")

        assert config != null
        assert config.getSources().size() == 1
        assert config.getSources()[0].getString("impl") == "files"
        assert config.getSources()[0].getStringList("paths") == ["${homeDir}/config"]

        true
    }

    def "propertyNames(Config) should return expected result"() {
        given:
        def map = [
            foo  : "bar",
            bar  : [
                a: 1,
                b: 2
            ],
            lists: [
                int_list: [1, 2, 3],
                obj_list: [
                    [a: "b"],
                    [b: "c"],
                ]
            ]
        ]
        def config = ConfigFactory.parseMap(map)

        when:
        def names = Tsc4jImplUtils.propertyNames(config)

        then:
        names == [
            'bar.a', 'bar.b', 'foo',
            'lists.int_list', 'lists.int_list[0]', 'lists.int_list[1]', 'lists.int_list[2]',
            'lists.obj_list', 'lists.obj_list[0]', 'lists.obj_list[1]'
        ] as Set
    }

    def "propertyNames(Config, boolean) should return expected result"() {
        given:
        def map = [
            foo  : "bar",
            bar  : [
                a: 1,
                b: 2
            ],
            lists: [
                int_list: [1, 2, 3],
                obj_list: [
                    [a: "b"],
                    [b: "c"],
                ]
            ]
        ]
        def config = ConfigFactory.parseMap(map)

        when:
        def names = Tsc4jImplUtils.propertyNames(config, appendList)

        then:
        names == expected

        where:
        appendList | expected
        true       | [
            'bar.a', 'bar.b', 'foo',
            'lists.int_list', 'lists.int_list[0]', 'lists.int_list[1]', 'lists.int_list[2]',
            'lists.obj_list', 'lists.obj_list[0]', 'lists.obj_list[1]'
        ] as Set
        false      | [
            'bar.a', 'bar.b', 'foo',
            'lists.int_list[0]', 'lists.int_list[1]', 'lists.int_list[2]',
            'lists.obj_list[0]', 'lists.obj_list[1]'
        ] as Set
    }

    def "getPropertyFromConfig('#name') should return '#expected'"() {
        given:
        def map = [
            foo  : "bar",
            bar  : [
                a: 1,
                b: 2
            ],
            lists: [
                int_list: [1, 2, 3],
                obj_list: [
                    [a: "b"],
                    [b: "c"],
                ]
            ]
        ]
        def config = ConfigFactory.parseMap(map)

        when:
        def result = Tsc4jImplUtils.getPropertyFromConfig(name, config)

        then:
        result == expected

        where:
        name                | expected
        'non.existent'      | null

        'foo'               | 'bar'
        'foo[0]'            | 'bar'
        'foo[1]'            | 'bar'

        'bar'               | [a: 1, b: 2]
        'bar.a'             | 1
        'bar.b'             | 2

        'lists.int_list'    | [1, 2, 3]
        'lists.int_list[0]' | 1
        'lists.int_list[1]' | 2
        'lists.int_list[2]' | 3
        'lists.int_list[3]' | null

        'lists.obj_list'    | [[a: "b"], [b: "c"]]
        'lists.obj_list[0]' | [a: "b"]
        'lists.obj_list[1]' | [b: "c"]
        'lists.obj_list[2]' | null
    }

    def "partitionList() should return expected result"() {
        when:
        def result = Tsc4jImplUtils.partitionList(list, max)

        then:
        result == expected

        where:
        list                               | max | expected
        []                                 | 1   | []
        [1]                                | 10  | [[1]]
        [1, 2]                             | 10  | [[1, 2]]
        [1, 2, 3]                          | 1   | [[1], [2], [3]]
        [1, 2, 3, 4]                       | 3   | [[1, 2, 3], [4]]
        [1, 2, 3, 4, 5, 6, 7, 8]           | 3   | [[1, 2, 3], [4, 5, 6], [7, 8]]
        [1, 2, 3, 4, 5, 6, 7, 8, 9]        | 3   | [[1, 2, 3], [4, 5, 6], [7, 8, 9]]
        [1, 2, 3, 4, 5, 6, 7, 8, 9] as Set | 3   | [[1, 2, 3], [4, 5, 6], [7, 8, 9]]
    }

    def "createBeanMapper() (#desiredType) should return instance of type: #expectedType"() {
        when:
        def mapper = Tsc4jImplUtils.loadBeanMapper(desiredType)
        log.info("got bean mapper: {}", mapper)

        then:
        mapper.getClass() == expectedType

        where:
        desiredType                                                 | expectedType
        ''                                                          | ReflectiveBeanMapper
        '  '                                                        | ReflectiveBeanMapper

        'ReflectiveBeanMapper'                                      | ReflectiveBeanMapper
        getClass().getPackage().getName() + '.ReflectiveBeanMapper' | ReflectiveBeanMapper
    }

    @RestoreSystemProperties
    def "beanMapper(#desiredType) should return desired type"() {
        given: "set system property with desired type"
        def propName = Tsc4jImplUtils.tsc4jPropName(Tsc4jImplUtils.PROP_BEAN_MAPPER)
        if (desiredType != null) {
            System.setProperty(propName, desiredType)
            log.info("set property {} -> {}", propName, desiredType)
        }

        when:
        def mapper = Tsc4jImplUtils.beanMapper()
        log.info("got bean mapper: {}", mapper)

        then:
        mapper.getClass() == expectedType

        cleanup:
        Tsc4jImplUtils.beanMapper = null

        where:
        desiredType                                                 | expectedType
        null                                                        | ReflectiveBeanMapper
        ''                                                          | ReflectiveBeanMapper
        '  '                                                        | ReflectiveBeanMapper

        'ReflectiveBeanMapper'                                      | ReflectiveBeanMapper
        getClass().getPackage().getName() + '.ReflectiveBeanMapper' | ReflectiveBeanMapper
    }

    def "beanMapper() should return singleton"() {
        when:
        def mappers = (1..10).collect { Tsc4jImplUtils.beanMapper() }
        def first = mappers.first()

        then:
        mappers.each { assert first.is(it) }

        cleanup:
        Tsc4jImplUtils.beanMapper = null
    }

    def "should load 2 bean mappers"() {
        when:
        def sw = new Stopwatch()
        def mappers = Tsc4jImplUtils.loadImplementations(BeanMapper)
        log.info("loaded: {} in {}", mappers, sw)

        then:
        mappers.size() == 1
        mappers[0] instanceof ReflectiveBeanMapper
    }

    def "newCache() should return empty cache"() {
        when:
        def cache = Tsc4jImplUtils.newCache("foo", Duration.ofSeconds(10))

        then:
        cache.size() == 0
        cache instanceof SimpleTsc4jCache
    }
}
