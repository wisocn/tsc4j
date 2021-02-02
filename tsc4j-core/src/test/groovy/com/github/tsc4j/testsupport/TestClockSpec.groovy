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

package com.github.tsc4j.testsupport

import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

@Unroll
class TestClockSpec extends Specification {
    def testZoneId = "Pacific/Apia"

    def "should create correct instance"() {
        given:
        def currentTime = System.currentTimeMillis()

        when:
        def clock = new TestClock()

        then:
        clock.millis() >= currentTime
        clock.millis() <= (currentTime + 50)
        clock.getZone() == ZoneId.systemDefault()

        when:
        clock = new TestClock(currentTime)

        then:
        clock.millis() == currentTime
        clock.instant() == Instant.ofEpochMilli(currentTime)
        clock.getZone() == ZoneId.systemDefault()

        when:
        clock = new TestClock(currentTime, ZoneId.of(testZoneId))

        then:
        clock.millis() == currentTime
        clock.instant() == Instant.ofEpochMilli(currentTime)
        clock.getZone() == ZoneId.of(testZoneId)
    }

    def "withZone() should return expected result"() {
        given:
        def timestamp = 100
        def targetZone = "Europe/London"
        def clock = new TestClock(timestamp, ZoneId.of("UTC"))

        expect:
        clock.millis() == timestamp
        clock.instant() == Instant.ofEpochMilli(timestamp)
        clock.getZone() == ZoneId.of("UTC")

        when:
        def result = clock.withZone(ZoneId.of(targetZone))

        then:
        result.getZone() == ZoneId.of(targetZone)
        result.millis() == timestamp
        result.instant() == Instant.ofEpochMilli(timestamp)
    }

    def "plus(long) should work"() {
        given:
        def curTimestamp = 1000
        def clock = new TestClock(curTimestamp)

        when:
        clock.plus(1000)

        then:
        clock.millis() == (1000 + curTimestamp)

        when:
        clock.setTimestamp(curTimestamp).plus(-1000)

        then:
        clock.millis() == 0
    }

    def "plus(duration, unit) should work"() {
        given:
        def curTimestamp = 1000
        def clock = new TestClock(curTimestamp)

        when:
        clock.plus(1, TimeUnit.SECONDS)

        then:
        clock.millis() == (1000 + curTimestamp)

        when:
        clock.setTimestamp(curTimestamp).plus(-1, TimeUnit.SECONDS)

        then:
        clock.millis() == 0
    }

    def "plus(duration) should work"() {
        given:
        def curTimestamp = 1000
        def clock = new TestClock(curTimestamp)

        when:
        clock.plus(Duration.ofSeconds(1))

        then:
        clock.millis() == (1000 + curTimestamp)

        when:
        clock.setTimestamp(curTimestamp).plus(Duration.ofSeconds(-1))

        then:
        clock.millis() == 0
    }
}
