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

package com.github.tsc4j.core

import beans.java.immutable.BeanWithRequiredListElements
import beans.java.immutable.ComplexImmutableBean
import beans.java.immutable.ComplexImmutableBean2
import beans.java.immutable.ComplexImmutableBean3
import beans.java.immutable.ImmutableBean
import beans.java.immutable.MyEnum
import beans.java.immutable.SimpleImmutableBean
import beans.java.mutable.SimpleFluentMutableBean
import beans.java.mutable.SimpleMutableBean
import com.github.tsc4j.core.impl.BeanWithStringObjectMap
import com.github.tsc4j.core.impl.BeanWithStringStringMap
import com.github.tsc4j.core.impl.Foo
import com.github.tsc4j.core.impl.FooBean
import com.github.tsc4j.core.impl.ImmutableFoo
import com.github.tsc4j.core.impl.MapWithBooleans
import com.github.tsc4j.core.impl.MapWithDoubles
import com.github.tsc4j.core.impl.MapWithPatterns
import com.github.tsc4j.core.impl.RewriteConfig
import com.github.tsc4j.core.impl.Stopwatch
import com.github.tsc4j.core.impl.VagueBean
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import groovy.util.logging.Slf4j
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZonedDateTime

@Slf4j
@Unroll
abstract class AbstractBeanMapperSpec extends Specification {

    def mapper = createMapper()
    def config = createConfig()
    def inConfig = ConfigFactory.parseResources("configs/config-for-beanmapper.conf").resolve()

    abstract AbstractBeanMapper createBeanMapper();

    private AbstractBeanMapper createMapper() {
        log.info("creating mapper")
        def sw = new Stopwatch()
        def mapper = createBeanMapper()
        log.info("created mapper {} in {}", mapper, sw)
        mapper
    }

    @RestoreSystemProperties
    def "should NOT discover custom config value converters"() {
        given:
        def propName = "tsc4j." + AbstractBeanMapper.PROP_NAME_CUSTOM_CONVERTERS_ENABLED
        System.setProperty(propName, "false")

        def mapper = createMapper()

        when:
        def registry = mapper.customValueConverters()

        then:
        registry.isEmpty()
        !mapper.getConfigValueConverter(InputStream).isPresent()
    }

    @RestoreSystemProperties
    def "should discover custom config value converters"() {
        given:
        if (value != null) {
            def propName = "tsc4j." + AbstractBeanMapper.PROP_NAME_CUSTOM_CONVERTERS_ENABLED
            System.setProperty(propName, value)
        }

        def mapper = createBeanMapper()

        when:
        def registry = mapper.customValueConverters()

        then:
        registry.size() == 1
        registry.get(InputStream).isPresent()
        mapper.getConfigValueConverter(InputStream).isPresent()

        where:
        value << [null, '', ' ', 'true']
    }

    def "should properly instantiate normal mutable bean"() {
        given:
        def firstBar = new FooBean.BarBean("aX", "aY")
        def secondBar = new FooBean.BarBean("bX", "bY")
        def thirdBar = new FooBean.BarBean("cX", "cY")

        when:
        def bean = mapper.create(FooBean, config)

        then:
        assert bean != null
        assert bean.getA() == "aString"
        assert bean.getB() == 42

        assert bean.getBars().size() == 3
        assert bean.getBars().get(0) == firstBar
        assert bean.getBars().get(1) == secondBar
        assert bean.getBars().get(2) == thirdBar
    }

    def "should properly instantiate normal mutable bean with fluent setters"() {
        given:
        def firstBar = new Foo.Bar("aX", "aY")
        def secondBar = new Foo.Bar("bX", "bY")
        def thirdBar = new Foo.Bar("cX", "cY")

        when:
        def bean = mapper.create(Foo, config)

        then:
        assert bean != null
        assert bean.getA() == "aString"
        assert bean.getB() == 42

        assert bean.getBars().size() == 3
        assert bean.getBars().get(0) == firstBar
        assert bean.getBars().get(1) == secondBar
        assert bean.getBars().get(2) == thirdBar
    }

    def "should properly instantiate immutable builder-supported class"() {
        given:
        def firstBar = new ImmutableFoo.ImmutableBar("aX", "aY")
        def secondBar = new ImmutableFoo.ImmutableBar("bX", "bY")
        def thirdBar = new ImmutableFoo.ImmutableBar("cX", "cY")

        when:
        def bean = mapper.create(ImmutableFoo, config)

        then:
        assert bean != null
        assert bean.getA() == "aString"
        assert bean.getB() == 42

        assert bean.getBars().size() == 3
        assert bean.getBars().get(0) == firstBar
        assert bean.getBars().get(1) == secondBar
        assert bean.getBars().get(2) == thirdBar
    }

    def "should properly convert Map<String,Object>"() {
        given:
        def map = [
            "x": 10,
            "y": "foo",
            "z": true
        ]
        def config = createConfig().withValue("some-map", ConfigValueFactory.fromAnyRef(map))

        when:
        def bean = mapper.create(BeanWithStringObjectMap, config)
        log.info("created bean: {} -> {}", bean.getClass(), bean)

        then:
        bean != null
        bean.getA() == "aString"
        bean.getB() == 42
        bean.getSomeMap() == map
    }

    def "should properly convert Map<String,String>"() {
        given:
        def map = [
            "x": 10,
            "y": "foo",
            "z": true
        ]
        def config = createConfig().withValue("some-map", ConfigValueFactory.fromAnyRef(map))

        when:
        def bean = mapper.create(BeanWithStringStringMap, config)

        then:
        bean != null
        bean.getA() == "aString"
        bean.getB() == 42

        bean.getSomeMap().size() == 3
        bean.getSomeMap()["x"] == map["x"].toString()
        bean.getSomeMap()["y"] == map["y"].toString()
        bean.getSomeMap()["z"] == map["z"].toString()
    }

    def "should work around complex immutable class hierarchy"() {
        given:
        def hocon = """
              strings: [
                "don@funny.com"
                "joe@example.com",
                "don@funny.com"
              ]

              rules: [
                {
                  pattern: "^/foo/(.*)"
                  replacement: "/bar/\$1"
                }

                {
                  pattern: "^/why/(.*)"
                  replacement: "/tho/\$1"
                }
              ]
            """

        def config = ConfigFactory.parseString(hocon)

        when:
        def bean = mapper.create(RewriteConfig, config)
        log.info("created bean: {}", bean)

        then:
        bean != null

        bean.getStrings() == ["joe@example.com", "don@funny.com"] as Set

        bean.getRules().size() == 2

        bean.getRules()[0].getPattern().toString() == '^/foo/(.*)'
        bean.getRules()[0].getReplacement() == '/bar/$1'
        bean.getRules()[1].getPattern().toString() == '^/why/(.*)'
        bean.getRules()[1].getReplacement() == '/tho/$1'
    }

    def "should properly deserialize vague bean"() {
        given:
        def hocon = """
    something: [
        {
            enabled: false
            impl: Mock
            patterns: [
                "@example.org\$",
                "^foo-.*foo.com\$"
            ]
            secrets: [
                first
                second
            ]
        },

        {
            enabled: false
            impl: Secure
            foo: bar
            x: [ "a", "b", "c" ]
            y: {
                foo: bar
                list: [ 1, 2, 3 ]
            }
        }
    ]
"""
        def config = ConfigFactory.parseString(hocon)

        when:
        def bean = mapper.create(VagueBean, config)
        log.info("created bean: {}", bean)

        then:
        bean.getSomething().size() == 2

        when:
        def first = bean.getSomething()[0]
        def second = bean.getSomething()[1]

        then:
        first.enabled == false
        first.impl == "Mock"
        first.patterns instanceof List
        first.patterns == ['@example.org$', '^foo-.*foo.com$']
        first.secrets == ['first', 'second']

        second.impl == 'Secure'
        second.foo == 'bar'
        second.x == ['a', 'b', 'c']
        second.y == [
            foo : 'bar',
            list: [1, 2, 3]
        ]
        second.enabled == false
    }

    def "should deserialize bean with a map field with pattern values"() {
        given:
        def configStr = '''
            {
                enabled: true
                patterns: {
                    first: "^/foo "
                    second: " ^/bar/(.+) "
                }
            }
        '''
        def config = ConfigFactory.parseString(configStr)

        when:
        def bean = mapper.create(MapWithPatterns, config)

        then:
        bean.isEnabled() == true

        def patterns = bean.getPatterns()
        with(patterns) {
            size() == 2
            get('first').toString() == "^/foo"
            get('second').toString() == "^/bar/(.+)"
        }
    }

    def "should deserialize bean with a map field with boolean values"() {
        given:
        def configStr = '''
            {
                enabled: true
                patterns: {
                    first: true
                    second: false
                    third: ""
                    fourth: bah # should deserialize to false
                    fifth: null
                }
            }
        '''
        def config = ConfigFactory.parseString(configStr)

        when:
        def bean = mapper.create(MapWithBooleans, config)

        then:
        bean.isEnabled() == true
        bean.getPatterns().size() == 5
        bean.getPatterns().get('first') == true
        bean.getPatterns().get('second') == false
        bean.getPatterns().get('third') == false
        bean.getPatterns().get('fourth') == false
    }

    def "should deserialize bean with a map field with double values"() {
        given:
        def configStr = '''
            {
                enabled: true
                patterns: {
                    first: 1.2
                    second: 3.2
                    third: 0 # should deserialize to 0
                }
            }
        '''
        def config = ConfigFactory.parseString(configStr)

        when:
        def bean = mapper.create(MapWithDoubles, config)

        then:
        bean.isEnabled() == true
        bean.getPatterns().size() == 3
        bean.getPatterns().get('first') == 1.2
        bean.getPatterns().get('second') == 3.2
        bean.getPatterns().get('third') == 0
    }

    def "should deserialize immutable bean with lombok defined and manually overridden builder classes"() {
        given:
        def configStr = '''
            a-boolean: true
            an-int: 42
            a-long: 9234567890
            a-double: 9234567890.9234567890
            a-string: "foobar"
            
            entry-list: [
                { x: "foo", y : 15},
                {},
                { y : 16},
                { x: "khm" }
            ]
            
            entry-set: [
                { x: "khm" }
                { x: "khm" }
                { x: "khm" }
                { x: "foo", y: 210}
            ]
            
            #int_bool_map: {
            #    42:  true
            #    "43":false
            #    "44": "true" 
            #}
        '''
        def config = ConfigFactory.parseString(configStr)
        log.info("got config: {}", config)

        expect:
        !config.isEmpty()

        when:
        def bean = mapper.create(ImmutableBean, config)
        log.info("deserialized bean: {}", bean)

        then:
        bean != null

        bean.isABoolean() == true
        bean.getAnInt() == 42
        bean.getALong() == 9234567890
        bean.getADouble() == 9234567890.9234567890
        bean.getAString() == "foobar"

        when:
        def list = bean.getEntryList()

        then:
        list[0].getX() == 'foo'
        list[0].getY() == 15

        list[1].getX() == 'blah'
        list[1].getY() == 41

        list[2].getX() == 'blah'
        list[2].getY() == 16

        list[3].getX() == 'khm'
        list[3].getY() == 41

        when:
        def set = bean.getEntrySet()

        then:
        set.size() == 2
        set == [
            new ImmutableBean.ImmutableBeanEntry("khm", 41),
            new ImmutableBean.ImmutableBeanEntry("foo", 210)] as Set
    }

    def "should correctly deserialize simple mutable bean"() {
        when:
        def bean = mapper.create(SimpleMutableBean, inConfig, 'foo.complex-bean')
        log.info("created bean: $bean")

        then:
        bean instanceof SimpleMutableBean

        with(bean) {
            getDuration() == Duration.ofSeconds(31)
            getPeriod() == Period.ofDays(6)
            getMinUsedBytes().toBytes() == 2 * 1024 * 1024
            getMaxUsedBytes().toBytes() == (long) 5 * 1024 * 1024 * 1024
            getTotalUsedBytes().toBytes() == 100 * 1024

            getZonedDateTime().withFixedOffsetZone() == ZonedDateTime.parse("2019-08-25T22:15:38Z")
            getLocalDateTime() == LocalDateTime.parse("2019-08-25T22:15:38")
            getLocalDate() == LocalDate.parse("2019-08-25")

            // date stuff is nasty due to time zones
            def date = getDate()
            def instant = date.toInstant()
            instant == getZonedDateTime().toInstant()

            getUuid().toString() == '9f8c8c97-f133-45f0-b132-326907d2b43d'
            getRegex().toString() == '^/foo.*'

            isABoolean() == true
            getAString() == ' super-string  '
            getAnInt() == 42
            getALong() == 3221225472L
            getAFloat() == 42.42F
            getADouble() == 3221225472.3221225472D
            getAnEnum() == MyEnum.TWO
        }
    }

    def "should correctly deserialize simple fluent mutable bean"() {
        when:
        def bean = mapper.create(SimpleFluentMutableBean, inConfig, 'foo.complex-bean')
        log.info("created bean: $bean")

        then:
        bean instanceof SimpleFluentMutableBean

        with(bean) {
            getDuration() == Duration.ofSeconds(31)
            getPeriod() == Period.ofDays(6)
            getMinUsedBytes().toBytes() == 2 * 1024 * 1024
            getMaxUsedBytes().toBytes() == (long) 5 * 1024 * 1024 * 1024
            getTotalUsedBytes().toBytes() == 100 * 1024

            getZonedDateTime().withFixedOffsetZone() == ZonedDateTime.parse("2019-08-25T22:15:38Z")
            getLocalDateTime() == LocalDateTime.parse("2019-08-25T22:15:38")
            getLocalDate() == LocalDate.parse("2019-08-25")

            // date stuff is nasty due to time zones
            def date = getDate()
            def instant = date.toInstant()
            instant == getZonedDateTime().toInstant()

            getUuid().toString() == '9f8c8c97-f133-45f0-b132-326907d2b43d'
            getRegex().toString() == '^/foo.*'

            isABoolean() == true
            getAString() == ' super-string  '
            getAnInt() == 42
            getALong() == 3221225472L
            getAFloat() == 42.42F
            getADouble() == 3221225472.3221225472D
            getAnEnum() == MyEnum.TWO
        }
    }

    def "should correctly deserialize simple immutable bean"() {
        when:
        def bean = mapper.create(SimpleImmutableBean, inConfig, 'foo.complex-bean')
        log.info("created bean: $bean")

        then:
        bean instanceof SimpleImmutableBean

        with(bean) {
            getDuration() == Duration.ofSeconds(31)
            getPeriod() == Period.ofDays(6)
            getMinUsedBytes().toBytes() == 2 * 1024 * 1024
            getMaxUsedBytes().toBytes() == (long) 5 * 1024 * 1024 * 1024
            getTotalUsedBytes().toBytes() == 100 * 1024

            getZonedDateTime().withFixedOffsetZone() == ZonedDateTime.parse("2019-08-25T22:15:38Z")
            getLocalDateTime() == LocalDateTime.parse("2019-08-25T22:15:38")
            getLocalDate() == LocalDate.parse("2019-08-25")

            // date stuff is nasty due to time zones
            def date = getDate()
            def instant = date.toInstant()
            instant == getZonedDateTime().toInstant()

            getUuid().toString() == '9f8c8c97-f133-45f0-b132-326907d2b43d'
            getRegex().toString() == '^/foo.*'

            isABoolean() == true
            getAString() == ' super-string  '
            getAnInt() == 42
            getALong() == 3221225472L
            getAFloat() == 42.42F
            getADouble() == 3221225472.3221225472D
            getAnEnum() == MyEnum.TWO
        }
    }

    def "should correctly deserialize immutable Config"() {
        given:
        def path = 'foo.complex-bean'
        def subConfig = inConfig.getConfig(path)

        when:
        def bean = mapper.create(Config.class, inConfig, path)
        log.info("deserialized config: {}", bean)

        then:
        bean instanceof Config
        !bean.isEmpty()
        bean == subConfig

        when:
        def newBean = mapper.create(Config.class, subConfig.root(), "")

        then:
        newBean == bean
    }

    def "should deserialize complex immutable bean"() {
        given:
        def path = 'complex-immutable-bean'
        def subConfig = inConfig.getConfig(path)

        when:
        def bean = mapper.create(ComplexImmutableBean, subConfig)
        log.info("created: {}", bean)

        then:
        def platforms = bean.getPlatforms()

        platforms.size() == 2
        platforms.keySet() == ['gcm', 'apns'] as Set

        def gcm = platforms.get('gcm')
        gcm.defaults == [concurrency: 350]
        gcm.senders.size() == 1
        gcm.senders.default == [platformDefault: true, apiKey: 'some-api-key-gcm']

        def apns = platforms.get('apns')
        apns.defaults == [numConnections : 2,
                          concurrency    : 500,
                          platformAliases: ['ios', 'iphone', 'ipad', 'ipod'],
                          production     : false]
        apns.senders.size() == 1
        apns.senders.foobar == [nameAliases      : ["foobarAlias"],
                                certificate      : "apns/some-cert.p12",
                                certificateSecret: "cert-apns-secret"]
    }

    def "should deserialize complex immutable bean 2"() {
        given:
        def path = 'complex-immutable-bean'
        def subConfig = inConfig.getConfig(path)

        when:
        def bean = mapper.create(ComplexImmutableBean2, subConfig)
        log.info("created: {}", bean)

        then:
        bean != null
    }

    def "should deserialize complex immutable bean 3"() {
        given:
        def path = 'complex-immutable-bean'
        def subConfig = inConfig.getConfig(path)

        when:
        def bean = mapper.create(ComplexImmutableBean3, subConfig)
        log.info("created: {}", bean)

        then:
        bean != null
    }

    def "should NOT create bean with list containing missing props"() {
        given:
        def cfgStr = '''
        {
          elements: [
             {
               "a": "firstA",
               "b": "firstB"
             },
             
             {
               # required `a` element is missing
               "b": "secondB"
             }
          ]
        }
'''
        def config = ConfigFactory.parseString(cfgStr)

        when:
        def bean = mapper.create(BeanWithRequiredListElements, config)
        log.info("created bean: {}", bean)

        then:
        def exception = thrown(Exception)
        bean == null

        cleanup:
        log.info("bean mapping exception:", exception)
    }

    def "should NOT create bean with list containing required non-null props"() {
        given:
        def cfgStr = '''
        {
          elements: [
             {
               "a": "firstA",
               "b": "firstB"
             },
             
             {
               "a": null
               "b": "secondB"
             }
          ]
        }
'''
        def config = ConfigFactory.parseString(cfgStr)

        when:
        def bean = mapper.create(BeanWithRequiredListElements, config)
        log.info("created bean: {}", bean)

        then:
        def exception = thrown(Exception)
        bean == null

        cleanup:
        log.info("bean mapping exception:", exception)
    }

    def "should create bean with list containing required props"() {
        given:
        def cfgStr = '''
        {
          elements: [
             {
               "a": "firstA",
               "b": "firstB"
             },
             
             {
               "a": "secondA"
               "b": "secondB"
             }
          ]
        }
'''
        def config = ConfigFactory.parseString(cfgStr)

        when:
        def bean = mapper.create(BeanWithRequiredListElements, config)
        log.info("created bean: {}", bean)

        then:
        bean != null

        def elements = bean.getElements()
        elements.size() == 2

        def first = elements[0]
        first.getA() == 'firstA'
        first.getB() == 'firstB'

        def second = elements[1]
        second.getA() == 'secondA'
        second.getB() == 'secondB'
    }

    def createConfig() {
        def bars = [
            ["x": "aX", "y": "aY"],
            ["x": "bX", "y": "bY"],
            ["x": "cX", "y": "cY"],
        ]

        ConfigFactory.empty()
                     .withValue("a", ConfigValueFactory.fromAnyRef("aString"))
                     .withValue("b", ConfigValueFactory.fromAnyRef(42))
                     .withValue("bars", ConfigValueFactory.fromAnyRef(bars))
    }
}
