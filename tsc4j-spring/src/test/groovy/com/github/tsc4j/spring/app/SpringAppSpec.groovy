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
import com.github.tsc4j.spring.Tsc4jHealthIndicator
import com.github.tsc4j.spring.Tsc4jSpringContextRefresher
import com.typesafe.config.Config
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import spock.lang.Ignore
import spock.lang.Unroll

@Slf4j
@Unroll
class SpringAppSpec extends SpringSpec {
    @Autowired
    ApplicationContext ctx

    @Autowired
    ReloadableConfig reloadableConfig

    @Autowired
    Config config

    def "context should be wired"() {
        expect:
        ctx != null
        reloadableConfig != null
        config != null

        ctx.getBean(ReloadableConfig) != null
        ctx.getBean(Config) != null
        ctx.getBean(Tsc4jSpringContextRefresher) != null
    }

    @Ignore
    def "spring context should return correctly extract configuration property values from hocon config"() {
        when:
        def name = ctx.getEnvironment().getProperty("spring.application.name")

        then:
        verifyAll {
            name == "appNameFromAppYml"
            ctx.getEnvironment().getProperty('app.var2', Integer) == 42
            ctx.getEnvironment().getProperty("app.var3") == "overriden in funky/application.conf: " + name
        }
    }

    def "context env should contain correct configuration properties"() {
        given:
        def prefix = "test.bean."

        when:
        log.info("asking for environment")
        def env = ctx.getEnvironment()
        log.info("got environment, asking for props")

        then:
        env.getProperty("${prefix}aBoolean") == "true"
        env.getProperty("${prefix}aInt") == "42"
        env.getProperty("${prefix}aLong") == "42"
        env.getProperty("${prefix}aDouble") == "42.42"
        env.getProperty("${prefix}aString") == "foobar"
    }

    @Ignore
    def "context should provide correct @ConfigurationProperties instance"() {
        when:
        def bean = ctx.getBean(beanClass)

        then:
        bean != null

        bean.isABoolean()
        bean.getAInt() == 42
        bean.getALong() == 42
        bean.getADouble() == 42.42
        bean.getAString() == "foobar"

        where:
        beanClass << [StandardBean, FluentBean]
    }
}
