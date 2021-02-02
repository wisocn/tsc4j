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

package com.github.tsc4j.core.impl

import com.github.tsc4j.core.AbstractBuilder
import com.github.tsc4j.core.Tsc4jLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
abstract class Tsc4jLoaderTestBaseSpec extends Specification {
    protected Logger log = LoggerFactory.getLogger(getClass())

    abstract Tsc4jLoader loader()

    abstract Class forClass()

    abstract Class builderClass()

    boolean shouldReturnConcreteInstance() {
        false
    }

    def "should be valid loader"() {
        when:
        def loader = loader()
        log.info("created loader: {}", loader)

        then:
        loader.forClass() != null
        !loader.name().isEmpty()

        loader.aliases() != null
        loader.aliases().every({ !it.isEmpty() && it.toLowerCase() == it })
        !loader.getInstance().isPresent()

        loader.getBuilder().get() instanceof AbstractBuilder
    }

    def "returned instance should provide expected types"() {
        when:
        def loader = loader()

        then:
        loader.forClass() == forClass()

        if (shouldReturnConcreteInstance()) {
            assert loader.getInstance().get() == forClass()
        } else {
            assert loader.getBuilder().get().getClass() == builderClass()
        }
    }

    def "supports() should return true to all combinations of name() and aliases"() {
        when:
        def loader = loader()
        def names = [loader.name()] + loader.aliases()

        then:
        names.every {
            assert loader.supports(it) == true
            assert loader.supports(' ' + it)
            assert loader.supports(' ' + it + ' ')
            assert loader.supports(' ' + it.toUpperCase() + ' ')

            true
        }
    }
}
