/*
 * Copyright 2017 - 2021 tsc4j project
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
 */

package com.github.tsc4j.spring.app

import com.github.tsc4j.api.ReloadableConfig
import com.github.tsc4j.spring.Tsc4jSpringContextRefresher
import com.typesafe.config.Config
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import spock.lang.Unroll

import javax.inject.Inject

@Unroll
class Tsc4JSpringConfigurationSpec extends SpringSpec {
    // ensure that @Inject to works as well
    @Inject
    ApplicationContext ctx

    @Autowired
    ReloadableConfig reloadableConfig

    @Autowired
    Config config

    def "should wire dependencies"() {
        expect:
        ctx != null
        reloadableConfig != null
        config != null
    }

    def "application context should provide singleton of #clazz"() {
        when:
        def beans = (1..100).collect({ ctx.getBean(clazz) })
        def first = beans.first()

        then:
        first != null
        beans.every({ it.is(first) })
        beans.every({ it == first })

        where:
        clazz << [ReloadableConfig, Tsc4jSpringContextRefresher]
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
