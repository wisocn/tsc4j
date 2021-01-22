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

import com.github.tsc4j.core.WithCache
import com.github.tsc4j.testsupport.TestClock
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import spock.lang.Specification

import java.time.Duration

class WithCacheSpec extends Specification {
    static def cacheTtl = Duration.ofMinutes(5)

    TestClock clock = new TestClock()

    def "default instance should have empty cache"() {
        given:
        def instance = instance()

        expect:
        instance.getCache().size() == 0
        !instance.getFromCache("foo").isPresent()
    }

    def "added item should be available until it expires"() {
        given:
        def instance = instance()

        def key = "someKey"
        def value = ConfigFactory.parseMap(["foo": "bar"])

        when:
        def res = instance.putToCache(key, value)

        then:
        res.is(value)

        instance.getFromCache(key).get() == value
        !instance.getFromCache("aaa").isPresent()

        when: "move time for 1 minute"
        clock.plus(Duration.ofMinutes(1))

        then: "entry should still be available"
        instance.getFromCache(key).get() == value
        !instance.getFromCache("aaa").isPresent()

        when: "move time past expiration, entries should be purged"
        clock.plus(cacheTtl)

        then:
        !instance.getFromCache(key).isPresent()
        !instance.getFromCache("aaa").isPresent()
    }

    WithCache<String, Config> instance() {
        def cache = cache()

        new WithCache<String, Config>() {
            @Override
            SimpleTsc4jCache<String, Config> getCache() {
                return cache
            }
        }
    }

    SimpleTsc4jCache<String, Config> cache() {
        new SimpleTsc4jCache<String, Config>("someName", cacheTtl, clock)
    }
}
