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

import com.github.tsc4j.api.ReloadableConfig
import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory
import groovy.util.logging.Slf4j
import org.springframework.boot.actuate.health.Status
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
@Slf4j
class Tsc4jHealthIndicatorSpec extends Specification {
    def reloadableConfig = Mock(ReloadableConfig)

    def "constructor should throw on null arguments"() {
        when:
        def indicator = new Tsc4jHealthIndicator(null)

        then:
        thrown(NullPointerException)
    }

    def "should return status down if reloadable config get() throws"() {
        given:
        def exception = new IOException("foo")
        def indicator = new Tsc4jHealthIndicator(reloadableConfig)

        when:
        def health = indicator.health()
        log.info("got health: {}", health)

        then:
        reloadableConfig.get() >> { throw exception }

        noExceptionThrown() // no exception should be thrown by health indicator
        health.getStatus() == Status.DOWN
        health.getDetails()["error"].toLowerCase().contains("reloadable config returned null config instance")
    }

    def "should return status down if reloadable config getSync() throws"() {
        given:
        def exception = new IOException("foo")
        def indicator = new Tsc4jHealthIndicator(reloadableConfig)

        when:
        def health = indicator.health()
        log.info("got health: {}", health)

        then:
        reloadableConfig.getSync() >> { throw exception }

        noExceptionThrown() // no exception should be thrown by health indicator
        health.getStatus() == Status.DOWN
        health.getDetails()["error"].contains("Exception")
    }

    def "should return UP if config is resolved"() {
        given:
        def config = Mock(Config)
        def indicator = new Tsc4jHealthIndicator(reloadableConfig)

        and: "setup config mock"
        config.isResolved() >> true
        config.root() >> ConfigValueFactory.fromMap([:])
        configClosure.call(config)

        when:
        def health = indicator.health()
        log.info("got health: {}", health)

        then:
        1 * reloadableConfig.getSync() >> config

        health.getStatus() == Status.UP

        when:
        def details = health.getDetails()

        then:
        details["paths"] instanceof Integer
        details["resolved"] == true
        details["empty"] instanceof Boolean
        details["hashcode"] instanceof Integer
        !details.containsKey("error")

        where:
        configClosure << [
            { it.isEmpty() >> true },
            { it.isEmpty() >> false },
        ]
    }

    def "should return DOWN if config is not resolved"() {
        given:
        def config = Mock(Config)
        def indicator = new Tsc4jHealthIndicator(reloadableConfig)

        when:
        def health = indicator.health()
        log.info("got health: {}", health)

        then:
        config.root() >> ConfigValueFactory.fromMap([:])
        config.isResolved() >> false
        config.isEmpty() >> true
        1 * reloadableConfig.getSync() >> config

        health.getStatus() == Status.DOWN
        health.getDetails()["error"].contains("is not resolved")
    }
}
