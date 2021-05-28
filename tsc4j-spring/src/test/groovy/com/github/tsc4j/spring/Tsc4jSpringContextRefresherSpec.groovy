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
import com.github.tsc4j.test.TestReloadable
import com.typesafe.config.ConfigFactory
import groovy.util.logging.Slf4j
import org.springframework.cloud.context.refresh.ContextRefresher
import org.springframework.core.env.Environment
import spock.lang.Specification
import spock.lang.Unroll

@Slf4j
@Unroll
class Tsc4jSpringContextRefresherSpec extends Specification {
    def reloadableConfig = Mock(ReloadableConfig)
    def ctxRefresher = Mock(ContextRefresher)

    def "constructor should throw on null args"() {
        when:
        def refresher = new Tsc4jSpringContextRefresher(ctxRefresher, reloadableConfig)

        then:
        thrown(NullPointerException)

        where:
        ctxRefresher           | reloadableConfig
        null                   | null
        Mock(ContextRefresher) | null
        null                   | Mock(ReloadableConfig)
    }

    def "should not refresh config on instantiation even if config is present"() {
        given:
        def reloadable = new TestReloadable()
        if (config != null) {
            log.warn("reloadable: {}", config)
            reloadable.set(config)
        }

        reloadableConfig.register(_) >> reloadable

        and:
        def refresher = new Tsc4jSpringContextRefresher(ctxRefresher, reloadableConfig)

        when:
        refresher.setEnvironment(Mock(Environment))

        then: "context refresher should not be touched"
        0 * ctxRefresher._
        refresher.numRefreshes.get() == 0

        where:
        config << [null, ConfigFactory.empty()]
    }

    def "should refresh context after config is reloaded"() {
        def reloadable = new TestReloadable()
        if (config != null) {
            log.warn("reloadable: {}", config)
            reloadable.set(config)
        }

        reloadableConfig.register(_) >> reloadable

        when:
        def refresher = new Tsc4jSpringContextRefresher(ctxRefresher, reloadableConfig)

        then: "context refresher should not be touched"
        0 * ctxRefresher._

        when: "reload config"
        def newConfig = ConfigFactory.parseMap(["a": "b"])
        reloadable.set(newConfig)

        then: "refresh should be called"
        1 * ctxRefresher.refresh()

        refresher.numRefreshes.get() == 1

        when: "assign the same config again"
        reloadable.set(newConfig)

        then: "refresh should not happen"
        0 * ctxRefresher._
        refresher.numRefreshes.get() == 1

        when: "assign new config"
        def evenNewerConfig = ConfigFactory.parseMap(["x": "y"])
        reloadable.set(evenNewerConfig)

        then: "should get refreshed"
        1 * ctxRefresher.refresh()
        refresher.numRefreshes.get() == 2

        when: "should not throw if context refresh throws"
        def exception = new IOException("foo")
        evenNewerConfig = ConfigFactory.parseMap("foo": "baz")
        reloadable.set(evenNewerConfig)

        then:
        1 * ctxRefresher.refresh() >> { throw exception }

        noExceptionThrown()
        refresher.numRefreshes.get() == 2

        where:
        config << [null, ConfigFactory.empty()]
    }

    def "should not refresh context if refresher is already closed"() {
        def reloadable = new TestReloadable()
        if (config != null) {
            log.warn("reloadable: {}", config)
            reloadable.set(config)
        }

        reloadableConfig.register(_) >> reloadable

        when:
        def refresher = new Tsc4jSpringContextRefresher(ctxRefresher, reloadableConfig)

        then:
        refresher.numRefreshes.get() == 0

        when: "close refresher and assign new config"
        refresher.close()
        def newConfig = ConfigFactory.parseMap(["a": "b"])
        reloadable.set(newConfig)

        then:
        def ex = thrown(IllegalStateException)
        ex.getMessage().toLowerCase().contains("is closed")

        0 * ctxRefresher._
        refresher.numRefreshes.get() == 0

        where:
        config << [null, ConfigFactory.empty()]
    }
}
