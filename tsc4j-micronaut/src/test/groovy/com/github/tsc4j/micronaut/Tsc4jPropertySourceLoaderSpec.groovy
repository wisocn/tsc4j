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

package com.github.tsc4j.micronaut

import io.micronaut.context.env.Environment
import io.micronaut.context.env.PropertySource
import io.micronaut.core.io.ResourceLoader
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class Tsc4jPropertySourceLoaderSpec extends Specification {
    @Shared
    def loader = new Tsc4jPropertySourceLoader()

    def cleanupSpec() {
        Utils.instanceHolder().close()
    }

    def "should return expected values"() {
        expect:
        loader.getOrder() == 9900
        loader.read("foo", Mock(InputStream)) == [:]
        loader.read("foo", new ByteArrayOutputStream().toByteArray()) == [:]
        loader.getExtensions() == [] as Set
    }

    def "should not return property source for resource name '#resourceName' and env '#envName'"() {
        given:
        def resourceLoader = Mock(ResourceLoader)
        def env = Mock(Environment)

        when:
        true

        then:
        0 * env._
        0 * resourceLoader._

        !loader.load(resourceName, resourceLoader, envName).isPresent()
        !loader.load(resourceName, env, envName).isPresent()

        where:
        [resourceName, envName] << [
            ["bootstrap", "foo", ""],
            ["foo", "bar", "baz"],
        ].combinations()
    }

    def "should return property source only when asked for application resource name"() {
        given:
        def env = Mock(Environment)
        env.getActiveNames() >> ["foo", "bar"]
        env.getPropertySources() >> new ArrayList<PropertySource>()

        def envNames = [null, 'foo', 'bar']

        and: "close loader"
        loader.close()

        expect: "should empty optional if asked for bootstrap name"
        envNames.each { assert !loader.load(Environment.BOOTSTRAP_NAME, env, it).isPresent() }

        when:
        def results = envNames.collect { loader.load(Environment.DEFAULT_NAME, env, it) }

        then:
        results.each { assert it.isPresent() }

        when:
        def sources = results.collect { it.get() }
        def first = sources[0]

        then: "the same source should be always returned."
        sources.each { assert it.is(first) }

        cleanup:
        first?.close()
    }
}
