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

package com.github.tsc4j.micronaut2

import com.github.tsc4j.api.ReloadableConfig
import com.github.tsc4j.core.Tsc4j
import com.typesafe.config.Config
import groovy.util.logging.Slf4j
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Value
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

@Slf4j
@MicronautTest
class MicronautAppSpec extends Specification {
    @Inject
    ReloadableConfig reloadableConfig

    @Inject
    Config config

    @Inject
    ApplicationContext appCtx

    @Value('${myapp.internal.list}')
    List<String> myList

    @Inject
    MyMicronautBean micronautBean

    private static final AtomicInteger counter = new AtomicInteger()

    def setup() {
        if (counter.incrementAndGet() == 1) {
            return
        };

        log.info("tsc4j fetched config:\n{}", Tsc4j.render(config, true))

        log.info("property source loaders")
        appCtx.environment.getPropertySourceLoaders().each {
            log.info("  {}", it)
        }

        log.info("property sources")
        appCtx.environment.getPropertySources().each {
            log.info("  {}", it)
        }
    }

    def "dependencies should be injected"() {
        expect:
        reloadableConfig != null
        config != null

        reloadableConfig.is(appCtx.getBean(ReloadableConfig))
        config == appCtx.getBean(Config)

        myList.size() == 6
        myList == ['a', 'b', 'c', 'c', 'b', 'a']
    }

    def "reloadable config should be available via application context as singleton"() {
        when:
        def results = (1..10).collect { appCtx.getBean(ReloadableConfig) }
        def last = results.pop()

        then:
        last != null
        results.every { it.is(last) }
    }

    def "config should be available via application context as prototype"() {
        when:
        def results = (1..10).collect { appCtx.getBean(Config) }
        def last = results.pop()

        log.info("got config: {}", Tsc4j.render(last, true))

        then:
        last != null
        !last.isEmpty()
        results.every { it != null && !it.isEmpty() }
    }

    def "groovy bean registration should work"() {
        when:
        def reloadable = reloadableConfig.register(MyBean)

        then:
        reloadable.isPresent()

        when:
        def bean = reloadable.get()

        then:
        bean.getA() == 'foo'
        bean.getB() == 'bar'
        bean.getList() == ['a', 'c', 'b'] as Set
    }

    def "fetching properties via application context should work"() {
        expect:
        appCtx.getProperty("myapp.internal.a", String).get() == "foo"
        appCtx.getProperty("myapp.internal.b", String).get() == "bar"

        appCtx.getProperty("myapp.internal.list", String).get() == "a,b,c,c,b,a"

        appCtx.getProperty("myapp.internal.list[0]", String).get() == "a"
        appCtx.getProperty("myapp.internal.list[1]", String).get() == "b"
        appCtx.getProperty("myapp.internal.list[2]", String).get() == "c"
        appCtx.getProperty("myapp.internal.list[3]", String).get() == "c"
        appCtx.getProperty("myapp.internal.list[4]", String).get() == "b"
        appCtx.getProperty("myapp.internal.list[5]", String).get() == "a"
    }

    def "configProperties bean should be correctly instantiated"() {
        expect:
        micronautBean.a == 'foo'
        micronautBean.b == 'bar'
        micronautBean.list == ['c', 'a', 'b'] as Set
    }

    def "context should contain expected values"() {
        given:
        def ctx = appCtx
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

        expect:
        expectedProps.each { assert appCtx.containsProperty(it) }         // all given properties must be set

        ctx.get('micronaut.application.id', String).get() == 'my-app-id'

        ctx.get('micronaut.io.watch.enabled', Boolean).get() == true
        ctx.get('micronaut.io.watch.restart', Boolean).get() == false
        ctx.get('micronaut.io.watch.check-interval', Duration).get() == Duration.ofSeconds(7)
        ctx.get('micronaut.io.watch.paths', List).get() == ['src/main', 'src/test/resources', '/foo/bar']

        ctx.get('foo.bar', List).get() == [[x: 'a', y: 'A'], [x: 'b', y: 'B']]

        ctx.get('x.y.foo', String).get() == 'bar'
        ctx.get('x.y.bar', String).get() == 'baz'
    }
}
