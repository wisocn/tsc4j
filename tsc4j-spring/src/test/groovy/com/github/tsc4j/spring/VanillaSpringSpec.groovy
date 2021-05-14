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

package com.github.tsc4j.spring

import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.core.env.Environment
import spock.lang.Stepwise

@Slf4j
@Stepwise
class VanillaSpringSpec extends SpringSpec {
    @Autowired
    ApplicationContext appCtx

    @Autowired
    Environment env

    def "fields should be injected"() {
        log.info("got ctx: {} {}", appCtx.getClass().getName(), appCtx.hashCode())
        log.info("got env: {} {}", env.getClass().getName(), env.hashCode())

        expect:
        appCtx != null
        env != null
    }

    def "env should contain expect config properties from application.yml"() {
        expect: "assert application.yml values"
        // map handling
        env.getProperty('some.map.foo') == 'bar'
        env.getProperty('some.map.bar') == 'baz'

        // map top-level element should return null 😱😱😱
        env.getProperty('some.map') == null
        env.getProperty('some.map', Map) == null

        // list handling
        env.getProperty('some.list[0]') == 'a'
        env.getProperty('some.list[1]') == 'b'
        env.getProperty('some.list[2]') == 'b'
        env.getProperty('some.list[3]') == 'c'
        env.getProperty('some.list[4]') == 'null'
        env.getProperty('some.list[5]') == '' // ymlnulls
        env.getProperty('some.list[6]') == 'd'
        env.getProperty('some.list[7]') == '39'
        env.getProperty('some.list[8]') == ' 39 '
        env.getProperty('some.list[9]') == null
        env.getProperty('some.list[10]') == 'd'

        //      list top level element should return null 😱😱😱
        env.getProperty('some.list') == null
        env.getProperty('some.list', List) == null

        when:
        def someMap = env.getProperty("some.map")
        log.info("got some map: {} {}", someMap.getClass().getName(), someMap)

        then:
        someMap != null

        when:
        def someList = env.getProperty("some.list")
        log.info("got some list: {} {}", someList.getClass().getName(), someList)

        then:
        someList != null
    }
}
