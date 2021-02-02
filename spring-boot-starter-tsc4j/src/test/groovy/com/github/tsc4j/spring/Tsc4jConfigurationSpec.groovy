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
import org.springframework.context.ApplicationContext

import javax.inject.Inject

class Tsc4jConfigurationSpec extends SpringSpec {
    @Inject
    ApplicationContext ctx

    @Inject
    ReloadableConfig reloadableConfig

    @Inject
    Config config

    def 'context should be wired'() {
        expect:
        ctx != null
    }

    def "reloadable config should be wired"() {
        expect:
        reloadableConfig != null
    }

    def "reloadable config should be singleton"() {
        when:
        def rcs = (1..100).collect({ ctx.getBean(ReloadableConfig) })

        then:
        rcs.every({ reloadableConfig == it })
        rcs.every({ it.is(reloadableConfig) })
    }

    def "config should be wired"() {
        expect:
        config != null
    }

    def "config should be the same until reloadable doesn't reload config"() {
        when:
        def configs = (1..100).collect({ ctx.getBean(Config) })

        then:
        configs.every({ it.is(config) })
        configs.every({ it == config })
    }

    def "config should contain expected values"() {
        given:
        def path = "test.bean"

        when:
        def cfg = config.getConfig(path)

        then:
        cfg != null
        cfg.getBoolean("aBoolean")
        cfg.getInt("aInt") == 42
        cfg.getLong("aLong") == 42
        cfg.getDouble("aDouble") == 42.42
        cfg.getString("aString") == "foobar"
    }
}
