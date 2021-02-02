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

import com.github.tsc4j.core.impl.SimpleTsc4jCache
import com.github.tsc4j.testsupport.TestClock
import com.typesafe.config.ConfigFactory
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

@Unroll
class CachedConfigSourceSpec extends Specification {
    def config = ConfigFactory.parseMap(a: UUID.randomUUID().toString())
    def delegate = Mock(ConfigSource)

    def clock = new TestClock()
    def cacheTtl = Duration.ofMinutes(5)

    def query = ConfigQuery.builder()
                           .appName("myApp")
                           .envs(["a", "b"])
                           .datacenter("foo")
                           .build()

    def "source should inherit allowErrors() from delegate"() {
        when:
        def source = new CachedConfigSource(delegate, testCache())

        delegate.allowErrors() >> flag

        then:
        source.allowErrors() == flag

        where:
        flag << [true, false]
    }

    def "close() should close the delegate"() {
        given:
        def cache = Mock(Tsc4jCache)

        when:
        new CachedConfigSource(delegate, cache).close()

        then:
        1 * delegate.close()
        1 * cache.clear()
    }

    def "exception from delegate should be propagated"() {
        given:
        def source = new CachedConfigSource(delegate, testCache())
        def exception = new RuntimeException("boom")

        when:
        def config = source.get(query)

        then:
        delegate.get(query) >> { throw exception }

        def thrown = thrown(RuntimeException)
        thrown.is(exception)

        config == null
        source.size() == 0
        !source.getFromCache(query).isPresent()
    }

    def "should ask source before returning cached result"() {
        given:
        def source = new CachedConfigSource(delegate, testCache())

        expect:
        source.size() == 0

        when:
        def res = source.get(query)

        then:
        1 * delegate.get(query) >> config

        res == config
        source.size() == 1

        when: "ask for config again, response should be cached"
        res = source.get(query)

        then: "delegate should not be contacted, fetched config should be from cache"
        0 * delegate._

        res == config
        source.size() == 1

        when: "move time forward for 1 minute, ask for config again"
        clock.plus(Duration.ofMinutes(1))

        res = source.get(query)

        then:
        0 * delegate._ // delegate shouldn't be invoked

        res == config
        source.size() == 1

        when: "move time forward past expiration time"
        clock.plus(cacheTtl)
        res = source.get(query)

        then:
        1 * delegate.get(query) >> config // delegate should be invoked again

        res == config
        source.size() == 1

        when: "perform cleanup"
        res = source.clear()

        then:
        res.is(source)
        source.size() == 0
    }

    Tsc4jCache testCache() {
        new SimpleTsc4jCache("cache", cacheTtl, clock)
    }
}
