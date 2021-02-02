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

package com.github.tsc4j

import com.github.tsc4j.core.Tsc4j
import com.github.tsc4j.core.Tsc4jImplUtils
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import org.slf4j.LoggerFactory
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

import static com.typesafe.config.ConfigValueFactory.fromAnyRef

@Unroll
class Tsc4jSpec extends Specification {
    def log = LoggerFactory.getLogger(getClass())

    def setupSpec() {
        ConfigFactory.invalidateCaches()
    }

    def cleanupSpec() {
        ConfigFactory.invalidateCaches()
    }

    def "renderConfig should render config correctly"() {
        given:
        def map = ["a": "b"]
        def config = ConfigFactory.parseMap(map)

        when:
        def json = Tsc4j.render(config)

        then:
        json == '{"a":"b"}'
    }

    def "renderConfig(pretty) should render config correctly"() {
        given:
        def map = ["a": "b"]
        def config = ConfigFactory.parseMap(map)

        when:
        def json = Tsc4j.render(config, true)

        then:
        json == "{\n    \"a\" : \"b\"\n}\n"
    }

    def "configPath('#path') should return '#expected'"() {
        given:
        def config = ConfigFactory.parseMap([a: "b"])

        when:
        def result = Tsc4j.configPath(path)

        then:
        if (!result.isEmpty()) {
            assert !config.hasPath(result)
        }

        result == expected

        where:
        path                   | expected
        null                   | ''
        ''                     | ''
        '  '                   | ''
        ' .'                   | ''
        '. '                   | ''
        ' . '                  | ''
        ' foo '                | 'foo'
        ' FoO '                | 'FoO'
        '.. foO..'             | 'foO'
        '.. foO .. '           | 'foO'
        '.. foO.'              | 'foO'
        '.. foO. '             | 'foO'
        '.. foO . '            | 'foO'
        ' @appId '             | 'appId'
        '💩foo'                | '"💩foo"'
        ' |💩foo 🤷|¯|_(ツ)_/¯' | '"|💩foo 🤷|¯|_(ツ)_/¯"'
    }

    def "validateString() should throw in case null arguments"() {
        when:
        def result = Tsc4jImplUtils.validateString(str, description)

        then:
        thrown(NullPointerException)
        result == null

        where:
        str  | description
        null | null
        null | "x"
        "x"  | null
    }

    def "validateString() should throw in case of bad input"() {
        when:
        def description = "some string"
        def result = Tsc4jImplUtils.validateString(str, description)

        then:
        def thrown = thrown(IllegalArgumentException)
        thrown.getMessage().contains(description)
        result == null

        where:
        str << ["", "   ", " č", "."]
    }

    def "validateString() should return expected result"() {
        expect:
        Tsc4jImplUtils.validateString(str, "description") == expected

        where:
        str     | expected
        " x"    | "x"
        "x "    | "x"
        "  x  " | "x"
    }

    def "withoutPaths() should remove specified paths"() {
        given:
        def configMap = [
            a              : "x",
            b              : [
                x: [
                    y: "foo",
                    w: "bar"
                ],
                y: [
                    z: "b.y.z"
                ]
            ],
            c              : "z",
            must_stay_empty: [:]
        ]
        def config = ConfigFactory.parseMap(configMap)

        and: "define paths that should be removed"
        def removePaths = [
            " a ",
            "foo.bar",
            " ",
            null,
            "b.x.y",
            "b.y.z",
            '@appId',
            ' |💩foo 🤷|¯\\_(ツ)_/¯']

        and: "define expected config"
        def expectedConfig = ConfigFactory.parseMap([
            b              : [x: [w: "bar"]],
            c              : "z",
            must_stay_empty: [:]
        ])

        when:
        def result = closure.call(config, removePaths)
        log.info("result: {}", Tsc4j.render(result))

        then:
        result == expectedConfig
        Tsc4j.render(result, true) == Tsc4j.render(expectedConfig, true)

        where:
        closure << [
            { cfg, it -> Tsc4j.withoutPaths(cfg, it) },
            { cfg, it -> Tsc4j.withoutPaths(cfg, ((List) it).toArray(new String[0])) },
        ]
    }

    def "withoutSystemProperties() should remove system props"() {
        given:
        def userHome = System.getProperty("user.home")
        def configString = '{ a: ${user.home} }'
        def myConfig = ConfigFactory.parseString(configString)
        def configWithSysProps = myConfig.withFallback(ConfigFactory.systemProperties()).resolve()
        log.info("config: {}", configWithSysProps)

        expect:
        !configWithSysProps.isEmpty()
        configWithSysProps.isResolved()
        configWithSysProps.getString("a") == userHome

        when:
        def result = Tsc4j.withoutSystemProperties(configWithSysProps)
        log.info("without system props: {}", result)

        then:
        result.isResolved()
        result.entrySet().size() == 1
        result.getString("a") == userHome
    }

    def "withoutPaths() should return config without specified paths"() {
        given:
        def cfgStr = '{a: "x", b.c: "y", c: "w"}'
        def config = ConfigFactory.parseString(cfgStr)

        when:
        def result = Tsc4j.withoutPaths(config, "a", "b.c")

        then:
        result != config

        result.root().size() == 1
        !result.hasPathOrNull("a")
        !result.hasPathOrNull("b.c")
        !result.hasPathOrNull("b")
        result.getString("c") == "w"
    }

    def "createTransformer() should return empty optional"() {
        given:
        def config = mapConfig(cfgMap)

        expect:
        !Tsc4jImplUtils.createTransformer(config, 1).isPresent()

        where:
        cfgMap << [
            [:],
            [impl: null],
            [impl: ""],
            [impl: "  "],
            [enabled: false],
        ]
    }

    def "createSource() should return empty optional"() {
        given:
        def config = mapConfig(cfgMap)

        expect:
        !Tsc4jImplUtils.createConfigSource(config, 1).isPresent()

        where:
        cfgMap << [
            [:],
            [impl: null],
            [impl: ""],
            [impl: "  "],
            [enabled: false],
        ]
    }

    def "withoutEnvVariables() should remove env vars"() {
        given:
        def config = ConfigFactory.systemEnvironment()

        expect:
        !config.isEmpty()
        config.root().size() == System.getenv().size()

        when:
        def result = Tsc4j.withoutEnvVars(config)

        then:
        result != config
        result.isEmpty()
    }

    def "withoutEnvVariables() should remove only env vars"() {
        given:
        def path = "foo.bar"
        def value = "someSuperValue"
        def config = ConfigFactory.systemEnvironment().withValue(path, fromAnyRef(value))

        expect:
        !config.isEmpty()
        config.root().size() == System.getenv().size() + 1

        when:
        def result = Tsc4j.withoutEnvVars(config)

        then:
        result != config
        !result.isEmpty()
        result.root().size() == 1
        result.getString(path) == value
    }

    def "withoutSystemPropertiesAndEnvVars() should remove only system props and env vars"() {
        given:
        def path = "foo.bar"
        def value = "someSuperValue"

        def config = ConfigFactory.systemEnvironment()
                                  .withFallback(ConfigFactory.systemProperties())
                                  .withValue(path, fromAnyRef(value))
        // log.info("config: {}", tsc4j.render(config, true))

        expect:
        config.root().size() > System.getenv().size()

        when:
        def result = Tsc4j.withoutSystemPropertiesAndEnvVars(config)

        then:
        !result.isEmpty()
        result.root().size() == 1
        result.getString(path) == value
    }

    def "resolveConfig() should add system props and env vars in order to resolve config, but result should not include them"() {
        given:
        def homeDir = System.getProperty("user.home")
        def config = ConfigFactory.parseString('''
            a: ${user.home}"/foo"
            b: ${HOME}/bar
            c: false
        ''')

        expect:
        config.root().size() == 3
        !config.isResolved()

        when:
        def result = Tsc4j.resolveConfig(config)

        then:
        result.isResolved()
        result.root().size() == 3

        result.getString("a") == "$homeDir/foo"
        result.getString("b") == "$homeDir/bar"
        !result.getBoolean("c")
    }

    @RestoreSystemProperties
    def "resolveConfig() should prefer system props to env variables"() {
        given: "add new system property"
        def path = 'HOME'
        def expectedValue = UUID.randomUUID().toString()

        and: "set system property"
        System.setProperty(path, expectedValue)

        and: "invalidate config caches and reload properties"
        ConfigFactory.invalidateCaches()

        and: "get config"
        def config = ConfigFactory.parseString('a: ${HOME}')

        expect:
        !System.getenv(path).isEmpty() // env variable with the same name as system property should be present
        System.getenv(path) != expectedValue
        System.getProperty(path) == expectedValue

        !ConfigFactory.systemEnvironment().getString(path).isEmpty()
        ConfigFactory.systemEnvironment().getString(path) != ConfigFactory.systemProperties().getString(path)

        config.root().size() == 1
        !config.isResolved()

        when:
        def result = Tsc4j.resolveConfig(config)

        then:
        result.isResolved()
        result.root().size() == 1
        result.getString("a") == expectedValue
    }

    def "version() should return something"() {
        when:
        def version = Tsc4j.version()
        log.info("retrieved tsc4j version: {}", version)

        then:
        !version.isEmpty()
    }

    def "versionProperties() should return info"() {
        when:
        def props = Tsc4j.versionProperties()
        log.info("retrieved version properties: {}", props)

        then:
        !props.isEmpty()
        !props.getProperty("git.build.version").isEmpty()
        !props.getProperty("git.commit.id.abbrev").isEmpty()
    }

    def "versionProperties() should always return new instance"() {
        when:
        def props = (1..10).collect({ Tsc4j.versionProperties() })
        def first = props.remove(0)

        then:
        props.size() == 9
        props.every { it == first } // all instances should be equal
        props.every { !it.is(first) } // yet they must not be the same object reference, because Properties is mutable
    }

    def "toBoostrapConfig() should resolve unresolved config"() {
        given:
        def homeDir = System.getProperty("user.home")
        def cfgString = '''
            sources = [
              {
                impl: "files"
                paths: [
                  ${user.home}"/config",
                ]
              }
            ]
        '''
        def rawConfig = ConfigFactory.parseString(cfgString)

        expect:
        !rawConfig.isEmpty()
        !rawConfig.isResolved()

        when: "convert to bootstrap config"
        def config = Tsc4j.toBootstrapConfig(rawConfig)

        then:
        config != null
        config.getSources().size() == 1
        config.getSources()[0].getString("impl") == "files"
        config.getSources()[0].getStringList("paths") == ["${homeDir}/config"]
    }

    @Ignore
    def "loadTsc4jConfig() should load correct classpath files"() {
        given:
        def envs = ["dev", "dev", "", null]

        when:
        def config = Tsc4jImplUtils.loadBootstrapConfig(envs)

        then:
        config != null
    }

    Config mapConfig(Map m) {
        ConfigValueFactory.fromMap(m).toConfig()
    }

    def "stringify() should produce expected result"() {
        expect:
        Tsc4j.stringify(value) == expected

        where:
        value                                                 | expected
        fromAnyRef(null)                                      | 'null'
        fromAnyRef(true)                                      | 'true'
        fromAnyRef(false)                                     | 'false'
        fromAnyRef(1.2)                                       | '1.2'
        fromAnyRef('foo ')                                    | 'foo '
        fromAnyRef(['a', 'b'])                                | 'a,b'
        fromAnyRef([true, false])                             | 'true,false'
        fromAnyRef([1.2, 2.3])                                | '1.2,2.3'
        fromAnyRef([a: 'b', c: 'd'])                          | '{"a":"b","c":"d"}'
        fromAnyRef([a: [true, false], c: [1.2, 2.3]])         | '{"a":[true,false],"c":[1.2,2.3]}'
        fromAnyRef([a: ["true", "false"], c: ["1.2", "2.3"]]) | '{"a":["true","false"],"c":["1.2","2.3"]}'
    }
}
