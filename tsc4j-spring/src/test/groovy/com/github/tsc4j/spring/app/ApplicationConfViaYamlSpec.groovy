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

import com.github.tsc4j.spring.SpringUtils
import groovy.transform.ToString
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import spock.lang.Ignore
import spock.lang.Stepwise
import spock.lang.Unroll

@Slf4j
@Stepwise
@Unroll
class ApplicationConfViaYamlSpec extends SpringSpec {
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

    @Ignore
    def "debug property sources"() {
        when:
        def propStr = SpringUtils.debugPropertySources(env)
        log.info("property sources:\n{}", propStr)

        then:
        true
    }

    def "env should contain expect config properties from application.yml"() {
        expect: "assert application.yml values"

        // simple values
        env.getProperty('yaml.some.bool') == 'true'
        env.getProperty('yaml.some.int') == '42'
        env.getProperty('yaml.some.another-int') == '42'

        // map top-level element should return null 😱😱😱
        env.getProperty('yaml.some.map') == null
        env.getProperty('yaml.some.map', Map) == null

        // map handling
        env.getProperty('yaml.some.map.foo') == 'bar'
        env.getProperty('yaml.some.map.bar') == 'baz'

        // list handling
        env.getProperty('yaml.some.list[0]') == 'a'
        env.getProperty('yaml.some.list[1]') == 'b'
        env.getProperty('yaml.some.list[2]') == 'b'
        env.getProperty('yaml.some.list[3]') == 'c'
        env.getProperty('yaml.some.list[4]') == 'null'
        env.getProperty('yaml.some.list[5]') == '' // yaml nulls get mapped into empty string
        env.getProperty('yaml.some.list[6]') == 'd'
        env.getProperty('yaml.some.list[7]') == '39'
        env.getProperty('yaml.some.list[8]') == ' 39 '
        env.getProperty('yaml.some.list[9]') == null

        //      list top level list element should return null 😱😱😱
        env.getProperty('yaml.some.list') == null
        env.getProperty('yaml.some.list', List) == null
    }

    def "should return string: #key"() {
        when:
        def value = env.getProperty(key)

        then:
        env.getProperty(key) != null
        env.getProperty(key) instanceof String

        where:
        key << [
            'yaml.some.map.foo',
            'yaml.some.map.bar',

            'yaml.some.list[0]',
            'yaml.some.list[1]',
            'yaml.some.list[2]',
            'yaml.some.list[3]',
            'yaml.some.list[4]',
            'yaml.some.list[5]',
            'yaml.some.list[6]',
            'yaml.some.list[7]',
            'yaml.some.list[8]',
        ]
    }

    def "should produce expected configproperties bean: A"() {
        when:
        def bean = appCtx.getBean(MyYamlBeanA)

        then:
        with(bean) {
            list == ['a', 'b', 'b', 'c', 'null', '', 'd', '39', ' 39 ']
            map == ['foo': 'bar', 'bar': 'baz']
        }
    }

    def "getting values should work as well"() {
        expect:
        env.resolvePlaceholders(text) == expected

        where:
        text                                                             | expected
        '${non.existent.a}'                                              | '${non.existent.a}'
        ' 🤯 ${non.existent.a} XX '                                      | ' 🤯 ${non.existent.a} XX '
        ' 🤯 ${non.existent.b:defaultValue}  '                           | ' 🤯 defaultValue  '
        ' 🤯${yaml.some.map.foo } 🤯'                                    | ' 🤯${yaml.some.map.foo } 🤯'
        ' 🤯${yaml.some.map.foo} 🤯'                                     | ' 🤯bar 🤯'
        '${yaml.some.map.bar} ${yaml.some.map.non-exist:hello}'          | 'baz hello'
        '${yaml.some.map.bar} ${yaml.some.map.foo} ${yaml.some.list[8]}' | 'baz bar  39 '
    }

    @Component
    @ConfigurationProperties("yaml.some")
    @ToString(includeNames = true, includePackage = false)
    static class MyYamlBeanA {
        List<String> list
        Map<String, String> map
    }

    def "should produce expected configproperties bean: B"() {
        when:
        def bean = appCtx.getBean(MyYamlBeanB)

        then:
        with(bean) {
            list == ['a', 'b', 'c', 'null', '', 'd', '39', ' 39 '].toSet()
            map.foo == 'bar'
            map.bar == 'baz'
            map.baz == null
        }
    }

    @Component
    @ConfigurationProperties("yaml.some")
    @ToString(includeNames = true, includePackage = false)
    static class MyYamlBeanB {
        Set<String> list
        MyFoo map
    }

    @ToString(includeNames = true, includePackage = false)
    static class MyFoo {
        String foo
        String bar
        String baz
    }

    def "spring context should contain component that declares @ConditionalOnProperty property defined in yaml"() {
        when:
        def component = appCtx.getBean(YamlConditionalFeature)

        then:
        component != null
        component.hello() == 'my-super-val-37'
    }

    @ToString(includeNames = true, includePackage = false)
    @Component
    @ConditionalOnProperty(name = "yaml.some.feature.enabled", havingValue = "true")
    @ConfigurationProperties("yaml.some.feature")
    static class YamlConditionalFeature {
        String someVal

        String hello() {
            someVal
        }
    }
}
