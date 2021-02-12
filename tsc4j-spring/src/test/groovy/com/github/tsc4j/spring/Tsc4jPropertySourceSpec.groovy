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

package com.github.tsc4j.spring

import com.github.tsc4j.api.Reloadable
import com.github.tsc4j.api.ReloadableConfig
import com.github.tsc4j.test.TestReloadable
import com.typesafe.config.ConfigFactory
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class Tsc4jPropertySourceSpec extends Specification {
    def randomId = UUID.randomUUID().toString()
    def cfgMap = [
        id : randomId,
        bar: "baz",
        a  : 'b',
        b  : 10,
        foo: [
            bar : "something",
            list: [1, 2, 3],
        ]
    ]
    def config = ConfigFactory.parseMap(cfgMap)
    def reloadable = new TestReloadable(config)
    def reloadableConfig = Mock(ReloadableConfig)

    def expectedPropertyNames = ['id', 'bar', 'a', 'b', 'foo.bar', 'foo.list', 'foo.list[0]', 'foo.list[1]', 'foo.list[2]']

    def setupSpec() {
        cleanupSpec()
    }

    def cleanupSpec() {
        SpringUtils.instanceHolder().close()
    }

    Tsc4jPropertySource newSource() {
        reloadableConfig.getSync() >> config
        reloadableConfig.register(_) >> reloadable
        new Tsc4jPropertySource(reloadableConfig)
    }

    def "constructors should throw on null arguments"() {
        when:
        def source = new Tsc4jPropertySource(null)

        then:
        thrown(RuntimeException)
        source == null

        when:
        source = new Tsc4jPropertySource(name, rc)

        then:
        thrown(RuntimeException)
        source == null

        where:
        name  | rc
        null  | null
        "foo" | null
        null  | Mock(ReloadableConfig)
    }

    def "waitForConfigFetch() should throw ISE until configuration is not fetched"() {
        given:
        def reloadable = new TestReloadable()
        reloadableConfig.register(_) >> reloadable

        and:
        def source = new Tsc4jPropertySource(reloadableConfig)

        when: "ask for current config"
        def config = source.waitForConfigFetch()

        then:
        thrown(IllegalStateException)
        config == null

        when: "supply config and ask for current config again"
        reloadable.set(this.config)
        config = source.waitForConfigFetch()

        then:
        config.is(this.config)
    }

    def "property fetching methods should throw ISE until configuration is not fetched"() {
        given:
        def reloadable = new TestReloadable()
        reloadableConfig.register(_) >> reloadable

        and:
        def source = new Tsc4jPropertySource(reloadableConfig)

        when:
        source.containsProperty("foo")

        then:
        thrown(IllegalStateException)

        when:
        source.getProperty("foo")

        then:
        thrown(IllegalStateException)

        when:
        source.getPropertyNames()

        then:
        thrown(IllegalStateException)

        when: "supply config"
        reloadable.set(config)

        then:
        source.containsProperty("id")
        source.containsProperty("foo.bar")
        source.containsProperty("bar")
        !source.containsProperty("baz")

        source.getProperty("id") == randomId
        source.getProperty("foo") == cfgMap["foo"]
        source.getProperty("bar") == cfgMap["bar"]
        source.getProperty("baz") == null

        source.getPropertyNames().length == expectedPropertyNames.size()
        source.getPropertyNames().toList().containsAll(expectedPropertyNames)

        when: "let's remove config"
        reloadable.clear()

        then: "instance should retain old values"
        source.containsProperty("id")
        source.containsProperty("foo.bar")
        source.containsProperty("bar")
        !source.containsProperty("baz")

        source.getProperty("id") == randomId
        source.getProperty("foo") == cfgMap["foo"]
        source.getProperty("bar") == cfgMap["bar"]
        source.getProperty("baz") == null

        source.getPropertyNames().length == expectedPropertyNames.size()
        source.getPropertyNames().toList().containsAll(expectedPropertyNames)

        when: "let's assign empty config"
        reloadable.set(ConfigFactory.empty())

        then: "config props should disappear"
        !source.containsProperty("id")
        !source.containsProperty("foo")
        !source.containsProperty("bar")
        !source.containsProperty("baz")

        source.getProperty("id") == null
        source.getProperty("foo") == null
        source.getProperty("bar") == null
        source.getProperty("baz") == null

        source.getPropertyNames().length == 0
        source.getPropertyNames().toList().isEmpty()
    }

    def "close() should close reloadable"() {
        given:
        def reloadable = Mock(Reloadable)
        reloadable.ifPresentAndRegister(_) >> reloadable
        reloadableConfig.register(_) >> reloadable

        and:
        def source = new Tsc4jPropertySource(reloadableConfig)

        when:
        source.close()

        then:
        1 * reloadable.close()
        noExceptionThrown()
    }

    def "getProperty()/containsProperty() should never throw for invalid property names"() {
        given: "setup config"
        def configMap = [a: 'b']
        def config = ConfigFactory.parseMap(configMap)

        and: "setup reloadable config"
        def reloadable = new TestReloadable(config)
        reloadableConfig.getSync() >> config
        reloadableConfig.register(_) >> reloadable

        and: "setup source"
        def source = new Tsc4jPropertySource(reloadableConfig)

        when:
        def result = source.getProperty(name)

        then:
        noExceptionThrown()
        result == null

        when: "check containsProperty()"
        result = source.containsProperty(name)

        then:
        noExceptionThrown()
        result == false

        where:
        name << [
            //null,
            '',
            '"',
            '\\',
            '/',
            '@appId',
            'foo:bar',
            'server.compression.mime-types[0]'
        ]
    }

    def "getProperty('#name')/containsProperty() return '#expected'"() {
        given: "setup source"
        def source = newSource()

        when:
        def result = source.getProperty(name)

        then:
        noExceptionThrown()
        result == expected

        when: "check containsProperty()"
        result = source.containsProperty(name)

        then:
        noExceptionThrown()
        result == (expected != null)

        where:
        name                           | expected
        'a'                            | 'b'
        'b'                            | 10
        'bar'                          | 'baz'

        'non.existent'                 | null
        'non.existent:defaultValue'    | null
        'non.existent[2]'              | null
        'non.existent[2]:defaultValue' | null

        'foo.bar:someDef'              | 'something'
        'foo.bar'                      | 'something'
        'foo.list'                     | [1, 2, 3]
        'foo.list[0]'                  | 1
        'foo.list[1]'                  | 2
        'foo.list[2]'                  | 3

        'foo.list[3]'                  | null
        '@appId'                       | null
        '.. foO..'                     | null
        ' @appId '                     | null
        '💩foo'                        | null
        ' |💩foo 🤷|¯|_(ツ)_/¯'         | null
    }
}
