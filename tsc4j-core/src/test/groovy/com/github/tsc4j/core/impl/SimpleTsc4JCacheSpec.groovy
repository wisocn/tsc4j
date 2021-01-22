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

import com.github.tsc4j.testsupport.TestClock
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

@Unroll
class SimpleTsc4JCacheSpec extends Specification {
    static def cacheName = "superCache"
    static def cacheTtl = Duration.ofMinutes(5)

    def log = LoggerFactory.getLogger(getClass())

    private TestClock clock = new TestClock()
    def pathA = "foo"
    def entryA = ConfigFactory.parseMap([a: "b"])

    def pathB = "bar"
    def entryB = ConfigFactory.parseMap([x: "y"])

    def "empty cache should be empty"() {
        given:
        def cache = new SimpleTsc4jCache<String, Config>(cacheName, cacheTtl, clock)

        expect:
        cache.size() == 0
        !cache.get(key).isPresent()

        where:
        [key] << [["", "foo", "bar"], [null]].combinations()
    }

    def "clear() should clear the cache"() {
        given:
        def cache = createCache()

        expect:
        cache.size() == 2

        when:
        def res = cache.clear()

        then:
        res.is(cache)
        cache.size() == 0
        !cache.get(pathA).isPresent()
        !cache.get(pathB).isPresent()
    }

    def "populated cache should return cached entries"() {
        given:
        def cache = createCache()

        expect:
        cache.get(pathA).get() == entryA
        cache.get(pathB).get() == entryB
    }

    def "cleanup() should remove obsolete entries"() {
        given:
        def cache = createCache()

        expect:
        cache.size() == 2

        when: "move clock forward, then perform maintenance"
        clock.plus(cacheTtl)
        def res = cache.maintenance()

        then:
        res.is(cache)
        cache.size() == 0

        !cache.get(pathA).isPresent()
        !cache.get(pathB).isPresent()
    }

    def "added key should be available in cache until it expires"() {
        given:
        def cache = createCache()

        def key = "myKey"
        def config = ConfigFactory.parseMap([foo: "xxxx"])

        expect:
        !cache.get(key).isPresent()

        when: "add to cache"
        def res = cache.put(key, config)

        then:
        res.is(cache)
        cache.get(key).get() == config
        !cache.get("blah").isPresent()

        when: "move clock 1 minute in the future"
        clock.plus(Duration.ofMinutes(1))

        then: "entry should be still available"
        cache.get(key).get() == config
        !cache.get("blah").isPresent()

        when: "move clock past expiration time"
        clock.plus(cacheTtl)

        then: "entry should be removed from cache"
        !cache.get(key).isPresent()
        !cache.get("blah").isPresent()
    }

    def "cache should run cleanup after certain amount of fetches"() {
        given:
        def cache = createCache()
        def key = "myKey"
        def config = ConfigFactory.parseMap([foo: "xxxx"])

        when: "move time forward, so that all"
        clock.plus(cacheTtl.plusSeconds(1))
        cache.put(key, config)

        then:
        cache.size() == 3

        // expired entries should still be there
        cache.cache.get(pathA) != null
        cache.cache.get(pathB) != null

        when: "ask for recently added key many times"
        1000.times { assert cache.get(key).isPresent() }

        then:
        cache.get(key).isPresent()
        cache.get(key).get() == config

        // expired entries should be expunged
        cache.cache.size() == 1
        cache.size() == 1
        cache.cache.get(pathA) == null
        cache.cache.get(pathB) == null
    }

    SimpleTsc4jCache<String, Config> createCache(Map entries = [:]) {
        def cache = new SimpleTsc4jCache<String, Config>(cacheName, cacheTtl, clock)

        if (!entries) {
            entries = [:]
            entries.put(pathA, entryA)
            entries.put(pathB, entryB)
        }

        entries.each { cache.put(it.key, it.value) }
        cache
    }
}
