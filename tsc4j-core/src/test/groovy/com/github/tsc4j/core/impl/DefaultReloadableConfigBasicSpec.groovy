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

import com.github.tsc4j.core.ConfigQuery
import com.github.tsc4j.core.ConfigSource
import com.typesafe.config.ConfigFactory
import groovy.util.logging.Slf4j
import spock.lang.Specification
import spock.lang.Timeout
import spock.lang.Unroll

import java.time.Duration
import java.util.function.Supplier

@Slf4j
@Unroll
class DefaultReloadableConfigBasicSpec extends Specification {
    static def appName = "my-app"
    static def envs = ["a", "b"]
    static def zone = "my-zone"
    static def datacenter = "my-datacenter"
    static def configQuery = ConfigQuery.builder()
                                        .appName(appName)
                                        .envs(envs)
                                        .datacenter(datacenter)
                                        .availabilityZone(zone)
                                        .build()

    ConfigSource configSource = Mock(ConfigSource)

    def "computeRefreshInterval() should throw NPE for null duration"() {
        when:
        DefaultReloadableConfig.computeRefreshInterval(null, jitter)

        then:
        thrown(NullPointerException)

        where:
        jitter << [0, 10, -2, 100]
    }

    def "computeRefreshInterval() should throw IAE for invalid jitter"() {
        when:
        DefaultReloadableConfig.computeRefreshInterval(Duration.ofSeconds(1), jitter)

        then:
        def exception = thrown(IllegalArgumentException)
        exception.getMessage().contains("Invalid refresh interval jitter")

        where:
        jitter << [-1, -2, 100, 102, 1000]
    }

    def "computeRefreshInterval() should return 0 for too short refresh durations."() {
        expect:
        DefaultReloadableConfig.computeRefreshInterval(duration, jitter) == 0

        where:
        [duration, jitter] << [
            [
                Duration.ofSeconds(0), Duration.ofSeconds(-1), Duration.ofSeconds(-10),
                Duration.ofMillis(99), Duration.ofMillis(10), Duration.ofMillis(1)
            ],
            [0, 10, 20, 100]
        ].combinations()
    }

    def "computeRefreshInterval() should compute #expected ms for #duration value with zero jitter"() {
        expect:
        DefaultReloadableConfig.computeRefreshInterval(duration, 0) == expected

        where:
        duration               | expected
        Duration.ofSeconds(1)  | 1_000
        Duration.ofSeconds(-1) | 0
        Duration.ofMillis(-0)  | 0
        Duration.ofMillis(10)  | 0
        Duration.ofNanos(100)  | 0
    }

    def "computeRefreshInterval() should never return less than 100 for short interval and max jitter"() {
        given:
        def interval = Duration.ofMillis(100) // smallest possible interval
        def jitterPctHint = 99 // maximum possible hint

        when: "ask for interval many times"
        def results = (1..10_000).collect {
            DefaultReloadableConfig.computeRefreshInterval(interval, jitterPctHint)
        } as Set

        then:
        results.size() >= 99
        results.each { assert it >= 100 && it <= 1999 }
    }

    def "computeRefreshInterval() should return value in correct bounds"() {
        given:
        def interval = Duration.ofSeconds(10)
        def jitterPctHint = 20

        when: "ask for interval many times"
        def results = (1..10_000).collect {
            DefaultReloadableConfig.computeRefreshInterval(interval, jitterPctHint)
        } as Set

        then:
        results.size() > 100
        results.each { assert it > 8000 && it < 12_000 }
    }

    def "isTooSmallRefreshInterval(#millis) should return #expected"() {
        expect:
        DefaultReloadableConfig.isTooSmallRefreshInterval(millis) == expected

        where:
        millis | expected
        1000   | false
        1001   | false
        10_001 | false
        10_001 | false
        999    | false
        99     | true
        50     | true
        0      | true
        -2     | true
        -1000  | true
    }

    def "constructor should throw NPE if required args are not set"() {
        if (configQuery && refresh) {
            return
        }

        when:
        def rc = DefaultReloadableConfig.builder()
                                        .configSupplier(configSupplier)
                                        .refreshInterval(refresh)
                                        .build()

        then:
        thrown(NullPointerException)
        rc == null

        where:
        [configSupplier, refresh] << [
            [Mock(Supplier), null],
            [Duration.ofSeconds(10), null],
        ].combinations()
    }

    @Timeout(1)
    def "constructor should immediately schedule refresh if duration is specified"() {
        given:
        def map = [foo: 'bar']
        def config = ConfigFactory.parseMap(map)

        and: "setup config source mock"
        configSource.get(configQuery) >> {
            Thread.sleep(120)
            config
        }

        when: "setup reloadable config and ask for fetch to be completed"
        def configSupplier = new ConfigSupplier(configSource, configQuery)
        def rc = DefaultReloadableConfig.builder()
                                        .refreshInterval(Duration.ofSeconds(1))
                                        .configSupplier(configSupplier)
                                        .build()
        log.info("created: {}", rc)

        // it take some time for scheduled executor service to start executing tasks.
        Thread.sleep(30)

        then:
        rc != null
        !rc.isPresent()
        !rc.isClosed()
        rc.isRefreshRunning()

        // check internal flags
        rc.refreshTicker != null
        rc.scheduledExecutor != null
        rc.shutdownScheduledExecutor == false

        when: "wait for configuration fetch to complete"
        def fetchedConfig = rc.getSync()
        log.info("rc after fetch: {}", rc)

        then:
        rc.isPresent()
        !rc.isClosed()

        fetchedConfig == config
        rc.get().toCompletableFuture().get() == fetchedConfig

        cleanup:
        rc?.close()
    }

    @Timeout(1)
    def "constructor should NOT trigger refresh if refresh-interval is too low "() {
        given:
        def map = [foo: 'bar']
        def config = ConfigFactory.parseMap(map)

        and: "setup config source mock"
        configSource.get(configQuery) >> {
            log.info("source was being asked via {}", it)
            Thread.sleep(120)
            log.info("source is responding")
            config
        }

        when: "setup reloadable config and ask for fetch to be completed"
        def configSupplier = new ConfigSupplier(configSource, configQuery)
        def rc = DefaultReloadableConfig.builder()
                                        .refreshInterval(Duration.ofSeconds(0))
                                        .configSupplier(configSupplier)
                                        .build()
        log.info("created: {}", rc)
        Thread.sleep(10) // just to make sure that refresh doesn't get triggered

        then:
        !rc.isPresent()
        !rc.isClosed()
        !rc.isRefreshRunning()

        // check internal flags
        rc.refreshTicker == null
        rc.scheduledExecutor == null
        rc.shutdownScheduledExecutor == false

        when: "wait for configuration fetch to complete"
        def fetchedConfig = rc.getSync()
        log.info("rc after fetch: {}", rc)

        then:
        rc.isPresent()
        !rc.isClosed()

        fetchedConfig == config
        rc.get().toCompletableFuture().get() == fetchedConfig

        cleanup:
        rc?.close()
    }
}
