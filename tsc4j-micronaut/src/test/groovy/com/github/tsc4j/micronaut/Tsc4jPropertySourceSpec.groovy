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

package com.github.tsc4j.micronaut

import com.github.tsc4j.api.Reloadable
import com.github.tsc4j.api.ReloadableConfig
import com.github.tsc4j.core.Tsc4j
import com.github.tsc4j.core.Tsc4jImplUtils
import com.github.tsc4j.core.impl.Stopwatch
import com.github.tsc4j.test.TestReloadable
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import groovy.util.logging.Slf4j
import io.micronaut.context.env.PropertySource
import spock.lang.Specification
import spock.lang.Unroll

import java.util.function.Function

@Slf4j
@Unroll
class Tsc4jPropertySourceSpec extends Specification {
    def reloadableConfig = Mock(ReloadableConfig)
    def reloadable = new TestReloadable<Config>()

    def "should throw in case of invalid args"() {
        when:
        def source = new Tsc4jPropertySource((ReloadableConfig) null)

        then:
        thrown(NullPointerException)
        source == null

        when: "null reloadable config"
        source = new Tsc4jPropertySource((Reloadable) null)

        then: "null reloadable"
        thrown(NullPointerException)
        source == null

        when: "realoadable that always returns null"
        source = new Tsc4jPropertySource(Mock(Reloadable))

        then:
        thrown(NullPointerException)
        source == null
    }

    def "should create new instance"() {
        when: "create via reloadable config constructor argument"
        def source = new Tsc4jPropertySource(reloadableConfig)

        then:
        1 * reloadableConfig.register(Function.identity()) >> reloadable
        source != null

        when: "when create via reloadable constructor argument"
        source = new Tsc4jPropertySource(reloadable)

        then:
        source != null
    }

    def "should return expected values"() {
        given:
        def reloadable = new TestReloadable()
        def source = new Tsc4jPropertySource(reloadable)

        expect:
        source.getName() == Tsc4jImplUtils.NAME
        source.getConvention() == PropertySource.PropertyConvention.JAVA_PROPERTIES
    }

    def "closing source should close reloadable"() {
        given:
        def reloadable = new TestReloadable()
        def source = new Tsc4jPropertySource(reloadable)

        expect:
        reloadable.checkClosed()

        when:
        source.close()

        then:
        true

        when: "check if reloadable is closed, it should throw ISE"
        reloadable.checkClosed()

        then:
        thrown(IllegalStateException)
    }

    def "get('#key') should return '#expected'"() {
        given:
        def config = ConfigFactory.defaultReference()
        def reloadable = new TestReloadable(config)

        and: "setup source"
        def source = new Tsc4jPropertySource(reloadable)

        expect:
        source.get(key) == expected

        where:
        key                   | expected
        'foo'                 | null
        'myapp.internal.foo'  | null
        'myapp.internal.a'    | 'foo'
        'myapp.internal.b'    | 'bar'

        'myapp.internal.list' | ['a', 'b', 'c', 'c', 'b', 'a']
    }

    def 'should provide expected config'() {
        given:
        def expectedProps = [
            'micronaut.application.id',

            'micronaut.io.watch.enabled',
            'micronaut.io.watch.restart',
            'micronaut.io.watch.check-interval',
            'micronaut.io.watch.paths',

            'foo.bar',

            'x.y.foo',
            'x.y.bar',
        ]

        and: "setup source"
        def sw = new Stopwatch()
        def config = Tsc4j.resolveConfig(ConfigFactory.load())
        log.info("loaded config in: {}", sw)

        def reloadable = new TestReloadable(config)
        def src = new Tsc4jPropertySource(reloadable)

        expect:
        src.getConvention() == PropertySource.PropertyConvention.JAVA_PROPERTIES
        src.getOrder() == 9900
        src.getName() == Tsc4jImplUtils.NAME

        // check that all expected propnames are there
        def names = src.iterator().toList()
        expectedProps.each { assert names.contains(it) }

        // check prop values
        src.get('micronaut.application.id') == 'my-app-id'

        src.get('micronaut.io.watch.enabled') == true
        src.get('micronaut.io.watch.restart') == false
        src.get('micronaut.io.watch.check-interval') == 'PT7S'
        src.get('micronaut.io.watch.paths') == ['src/main', 'src/test/resources', '/foo/bar']

        src.get('foo.bar') == [[x: 'a', y: 'A'], [x: 'b', y: 'B']]

        src.get('x.y.foo') == 'bar'
        src.get('x.y.bar') == 'baz'
    }
}
