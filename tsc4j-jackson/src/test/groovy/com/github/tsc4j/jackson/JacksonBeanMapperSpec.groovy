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

package com.github.tsc4j.jackson

import beans.java.immutable.ComplexImmutableBean
import com.github.tsc4j.core.AbstractBeanMapperSpec
import com.github.tsc4j.core.ReflectiveBeanMapper
import com.github.tsc4j.core.Tsc4jImplUtils
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import groovy.util.logging.Slf4j
import spock.lang.Unroll

import java.time.Duration

@Slf4j
@Unroll
class JacksonBeanMapperSpec extends AbstractBeanMapperSpec {
    @Override
    JacksonBeanMapper createBeanMapper() {
        new JacksonBeanMapper()
    }

    def "Tsc4jImplUtils.loadBeanMapper(#type) should return default bean mapper if requested"() {
        when:
        def mapper = Tsc4jImplUtils.loadBeanMapper(type)

        then:
        mapper instanceof ReflectiveBeanMapper

        where:
        type << [
            "reflective",
            "reflectivebeanmapper",
            "ReflectiveBeanMapper",
            "com.github.tsc4j.core.ReflectiveBeanMapper",
        ]
    }

    def "Tsc4jImplUtils.beanMapper() should return jackson bean mapper if it's on classpath by default"() {
        when:
        def mappers = (1..10).collect { Tsc4jImplUtils.beanMapper() }
        def first = mappers.first()

        then:
        first instanceof JacksonBeanMapper
        mappers.each { assert it.is(first) }
    }

    def "should deserialize config"() {
        given:
        def text = '''
        {
          "some-dur": "10s",
          "foo": "bar",
          "list": [ 1, 2, 3 ],
          "bar": {
            "baz": "bah",
            "x": {
              "a" : 1,
              "b": true
            }
          }
        }
        '''
        def config = ConfigFactory.parseString(text)

        when:
        def mappedConfig = mapper.create(Config.class, config.root(), "")
        log.info("parsed cfg: {}", mappedConfig)

        then:
        mappedConfig != null
        mappedConfig == config

        with(mappedConfig) {
            getString("foo") == "bar"
            getIntList("list") == [1, 2, 3]
            getString("bar.baz") == "bah"
            getString("bar.x.a") == "1"
            getInt("bar.x.a") == 1
            getString("bar.x.b") == "true"
            getBoolean("bar.x.b") == true
            getDuration("some-dur") == Duration.ofSeconds(10)
        }
    }

    def "Tsc4jImplUtils.loadBeanMapper(#type) should return jackson bean mapper"() {
        when:
        def mapper = Tsc4jImplUtils.loadBeanMapper(type)
        log.info("got bean mapper: {}", mapper)

        then:
        mapper instanceof JacksonBeanMapper

        where:
        type << [
            // jackson bean mapper should be preferred over default bean mapper due to getOrder() priority
            null, // preferred bean mapper
            '',
            ' ',

            // explicit setting
            'jackson',
            'jacksonbeanMapper',
            'JacksonBeanMapper',
            'com.github.tsc4j.jackson.JacksonBeanMapper',
        ]
    }

    def "should deserialize complex immutable bean XXXX"() {
        given:
        def path = 'complex-immutable-bean'
        def subConfig = inConfig.getConfig(path)

        when:
        def bean = mapper.create(ComplexImmutableBean, subConfig)
        log.info("created: {}", bean)

        then:
        bean != null

        when:
        def json = Jackson.get().writeValueAsString(bean)
        def newConfig = ConfigFactory.parseString(json).resolve()

        then:
        newConfig == subConfig
        !newConfig.is(subConfig)
    }
}
