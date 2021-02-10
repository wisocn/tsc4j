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

import com.github.tsc4j.api.Reloadable
import com.github.tsc4j.api.ReloadableConfig
import com.github.tsc4j.test.TestReloadable
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.runtime.context.scope.refresh.RefreshEvent
import spock.lang.Specification

class MicronautContextRefresherSpec extends Specification {
    def reloadable = new TestReloadable()
    def appCtx = Mock(ApplicationContext)

    def "constructor should throw on bad arguments"() {
        when:
        def refresher = new MicronautContextRefresher((ReloadableConfig) reloadable, (ApplicationContext) appCtx)

        then:
        thrown(NullPointerException)
        refresher == null

        where:
        [reloadable, appCtx] << [
            [null, null],
            [Mock(ReloadableConfig), null],
            [null, Mock(ApplicationContext)]
        ]
    }

    def "constructor should throw on bad arguments2"() {
        when:
        def refresher = new MicronautContextRefresher((Reloadable) reloadable, (ApplicationContext) appCtx)

        then:
        thrown(NullPointerException)
        refresher == null

        where:
        [reloadable, appCtx] << [
            [null, null],
            [Mock(Reloadable), null],
            [null, Mock(ApplicationContext)]
        ]
    }

    def "should not refresh context if the same configuration is assigned as it was on init"() {
        given:
        def firstConfig = ConfigFactory.empty()

        and: "setup app environment"
        def appEnv = Mock(Environment)
        def envDiffMap = [foo: "bar"]

        and: "assign config to reloadable, create refresher"
        reloadable.set(firstConfig)
        def refresher = newRefresher()

        when: "assign the same config"
        reloadable.set(firstConfig)

        then: "application context should not react"
        appCtx.getEnvironment() >> appEnv
        appEnv.refreshAndDiff() >> envDiffMap

        0 * appCtx._

        refresher != null
    }

    def "should not refresh context if reloadable contains config and is later updated with the same config"() {
        given:
        def firstConfig = ConfigFactory.empty()
        reloadable.set(firstConfig)

        and: "setup app environment"
        def appEnv = Mock(Environment)
        def envDiffMap = [foo: "bar"]

        and: "assign config to reloadable, create refresher"
        reloadable.set(firstConfig)
        def refresher = newRefresher()

        when: "assign the same config"
        reloadable.set(firstConfig)

        then: "application context should not react"
        appCtx.getEnvironment() >> appEnv
        appEnv.refreshAndDiff() >> envDiffMap

        0 * appCtx._

        refresher != null
    }

    def "should refresh context if configuration exists at creation time and is updated later"() {
        given:
        def firstConfig = ConfigFactory.empty()
        reloadable.set(firstConfig)
        def updatedConfig = firstConfig.withValue("foo", ConfigValueFactory.fromAnyRef("bar"))

        and: "setup app environment"
        def appEnv = Mock(Environment)
        def envDiffMap = [foo: "bar"]

        and: "assign config to reloadable, create refresher"
        reloadable.set(firstConfig)
        def refresher = newRefresher()

        when: "assign new config"
        reloadable.set(updatedConfig)

        then: "application context should not react"
        appCtx.getEnvironment() >> appEnv
        appEnv.refreshAndDiff() >> envDiffMap

        1 * appCtx.publishEvent({ it instanceof RefreshEvent })

        refresher != null
    }

    MicronautContextRefresher newRefresher() {
        new MicronautContextRefresher(reloadable, appCtx)
    }
}
