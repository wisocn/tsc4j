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

package com.github.tsc4j.springboot

import com.github.tsc4j.spring.SpringUtils
import groovy.transform.ToString
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import spock.lang.Stepwise
import spock.lang.Unroll

@Slf4j
@Stepwise
@Unroll
class ApplicationConfViaTsc4jSpec extends SpringSpec {
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

    def "debug property sources"() {
        when:
        def propStr = SpringUtils.debugPropertySources(env)
        log.info("property sources:\n{}", propStr)

        then:
        true
    }

    def "env should contain expect config properties from application-test.conf"() {
        expect: "assert application.yml values"
        // simple values
        env.getProperty('hocon.some.bool') == 'true'
        env.getProperty('hocon.some.int') == '42'
        env.getProperty('hocon.some.another-int') == '42'

        // map top-level element should return null 😱😱😱
        env.getProperty('yaml.some.map') == null
        env.getProperty('yaml.some.map', Map) == null

        // map handling
        env.getProperty('hocon.some.map.foo') == 'bar'
        env.getProperty('hocon.some.map.bar') == 'baz'

        // list handling
        env.getProperty('hocon.some.list[0]') == 'a'
        env.getProperty('hocon.some.list[1]') == 'b'
        env.getProperty('hocon.some.list[2]') == 'b'
        env.getProperty('hocon.some.list[3]') == 'c'
        env.getProperty('hocon.some.list[4]') == 'null'
        env.getProperty('hocon.some.list[5]') == '' // yaml nulls get mapped into empty string
        env.getProperty('hocon.some.list[6]') == 'd'
        env.getProperty('hocon.some.list[7]') == '39'
        env.getProperty('hocon.some.list[8]') == ' 39 '
        env.getProperty('hocon.some.list[9]') == null

        //      list top level list element should return null 😱😱😱
        // TODO: fix it
        //env.getProperty('hocon.some.list') == null
        //env.getProperty('hocon.some.list', List) == null
    }

    def "should return string: #key"() {
        when:
        def value = env.getProperty(key)

        then:
        env.getProperty(key) != null
        env.getProperty(key) instanceof String

        where:
        key << [
            'hocon.some.map.foo',
            'hocon.some.map.bar',

            'hocon.some.list[0]',
            'hocon.some.list[1]',
            'hocon.some.list[2]',
            'hocon.some.list[3]',
            'hocon.some.list[4]',
            'hocon.some.list[5]',
            'hocon.some.list[6]',
            'hocon.some.list[7]',
            'hocon.some.list[8]',
        ]
    }

    def "should produce expected configproperties bean: A"() {
        when:
        def bean = appCtx.getBean(MyHoconBeanA)
        log.info("got bean: {}", bean)

        bean.list.each { log.info("{} {}", it?.class?.name, it) }

        then:
        with(bean) {
            //list.size() == 2
            list == ['a', 'b', 'b', 'c', 'null', '', 'd', '39', ' 39 ']
            map == ['foo': 'bar', 'bar': 'baz']
        }
    }

    @Component
    @ConfigurationProperties("hocon.some")
    @ToString(includeNames = true, includePackage = false)
    static class MyHoconBeanA {
        List<String> list
        Map<String, String> map
    }

    def "should produce expected configproperties bean: B"() {
        when:
        def bean = appCtx.getBean(MyHoconBeanB)
        log.info("got bean: {}", bean)

        then:
        with(bean) {
            list == ['a', 'b', 'c', 'null', '', 'd', '39', ' 39 '].toSet()
            map.foo == 'bar'
            map.bar == 'baz'
            map.baz == null
        }
    }

    def "getting values should work as well"() {
        expect:
        env.resolvePlaceholders(text) == expected

        where:
        text                                                                | expected
        '${non.existent.a}'                                                 | '${non.existent.a}'
        ' 🤯 ${non.existent.a} XX '                                         | ' 🤯 ${non.existent.a} XX '
        ' 🤯 ${non.existent.b:defaultValue}  '                              | ' 🤯 defaultValue  '
        ' 🤯${hocon.some.map.foo } 🤯'                                      | ' 🤯${hocon.some.map.foo } 🤯'
        ' 🤯${hocon.some.map.foo} 🤯'                                       | ' 🤯bar 🤯'
        '${hocon.some.map.bar} ${hocon.some.map.non-exist:hello}'           | 'baz hello'
        '${hocon.some.map.bar} ${hocon.some.map.foo} ${hocon.some.list[8]}' | 'baz bar  39 '
    }

    @Component
    @ConfigurationProperties("hocon.some")
    @ToString(includeNames = true, includePackage = false)
    static class MyHoconBeanB {
        Set<String> list
        MyFoo map
    }

    @ToString(includeNames = true, includePackage = false)
    static class MyFoo {
        String foo
        String bar
        String baz
    }

    def "spring context should contain component that declares @ConditionalOnProperty property defined in hocon"() {
        when:
        def component = appCtx.getBean(Tsc4jConditionalFeature)

        then:
        component != null
        component.hello() == 'my-super-val-39'
    }

    @ToString(includeNames = true, includePackage = false)
    @Component
    @ConditionalOnProperty(name = "hocon.some.feature.enabled", havingValue = "true")
    @ConfigurationProperties("hocon.some.feature")
    static class Tsc4jConditionalFeature {
        String someVal

        String hello() {
            someVal
        }
    }
}
