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

import com.github.tsc4j.core.impl.Foo
import com.github.tsc4j.core.impl.ImmutableFoo
import com.typesafe.config.ConfigOrigin
import groovy.util.logging.Slf4j
import spock.lang.Unroll

@Slf4j
@Unroll
class ReflectiveBeanMapperSpec extends AbstractBeanMapperSpec {
    @Override
    ReflectiveBeanMapper createBeanMapper() {
        return new ReflectiveBeanMapper()
    }

    def "should use builder for class #clazz: #expected"() {
        expect:
        mapper.shouldUseBuilder(clazz) == expected

        where:
        clazz        | expected
        Foo          | false
        ImmutableFoo | true
    }

    def "createBuilder() should create correct builder"() {
        when:
        def builder = mapper.createBuilder(ImmutableFoo)

        then:
        builder != null
        builder.getClass() == ImmutableFoo.ImmutableFooBuilder
    }

    def "createBeanInstance() should create expected instance"() {
        given:
        def builder = ImmutableFoo.builder()
                                  .a("foo")
                                  .b(42)
                                  .bars([new ImmutableFoo.ImmutableBar("b", "c")])
        def expectedInstance = builder.build()

        when:
        def instance = mapper.createBeanInstance(ImmutableFoo, builder, Mock(ConfigOrigin), "foo.bar")

        then:
        instance != null
        instance.getClass() == ImmutableFoo

        instance == expectedInstance
        !instance.is(expectedInstance)
    }

    def "should return correct setters for immutable builder supported class"() {
        given:
        def clazz = ImmutableFoo.ImmutableFooBuilder

        when:
        def setters = mapper.getSetters(clazz)
        log.info("retrieved {} setters", setters.size())
        setters.each { log.info("  $it") }

        then:
        setters.size() == 3
    }

    def "should return correct setters for mutable bean"() {
        given:
        def clazz = Foo

        when:
        def setters = mapper.getSetters(clazz)
        log.info("retrieved {} setters", setters.size())
        setters.each { log.info("  $it") }

        then:
        setters.size() == 3
    }
}
