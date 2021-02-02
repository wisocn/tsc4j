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

package com.github.tsc4j.test

import com.github.tsc4j.api.Reloadable
import com.github.tsc4j.core.impl.AbstractReloadableSpec
import com.github.tsc4j.test.TestReloadable

class TestReloadableSpec extends AbstractReloadableSpec {
    @Override
    protected <T> Reloadable<T> emptyReloadable() {
        return new TestReloadable<T>()
    }

    @Override
    protected <T> Reloadable<T> createReloadable(T value) {
        new TestReloadable<>(value)
    }

    @Override
    protected <T> Reloadable<T> updateReloadable(Reloadable<T> reloadable, T value) {
        ((TestReloadable<T>) reloadable).set(value)
    }

    @Override
    protected <T> Reloadable<T> removeReloadableValue(Reloadable<T> reloadable) {
        ((TestReloadable<T>) reloadable).clear()
    }

    def "calling empty constructor should create empty reloadable"() {
        when:
        def reloadable = new TestReloadable()

        then:
        !reloadable.isPresent()
        reloadable.registered().isEmpty()
    }

    def "calling with value constructor with null argument should throw"() {
        when:
        def reloadable = new TestReloadable(null)

        then:
        thrown(NullPointerException)
        reloadable == null
    }

    def "of() should return expected result"() {
        when:
        def reloadable = TestReloadable.of(value)

        then:
        reloadable.isPresent() == isPresent
        reloadable.registered().isEmpty()

        where:
        value | isPresent
        null  | false
        "foo" | true
    }

    def "set() should throw on null arguments"() {
        when:
        def r = reloadable.set(null)

        then:
        thrown(NullPointerException)
        r == null

        where:
        reloadable << [new TestReloadable<>(), new TestReloadable<>("foo")]
    }

    def "set() should set value"() {
        given:
        def reloadable = emptyReloadable()
        def updatedValue = "foo"

        when:
        def r = reloadable.set(updatedValue)

        then:
        r.is(reloadable)

        reloadable.isPresent()
        reloadable.get().is(updatedValue)
    }

    def "clear() should remove value and run consumers on reloadable that contains value"() {
        given:
        def reloadable = createReloadable("foo")

        def consumedValue = "bar"
        def invoked = false

        and: "setup consumer"
        reloadable.register({ invoked = true; consumedValue = it })

        when:
        def r = reloadable.clear()

        then:
        r.is(reloadable)

        reloadable.isEmpty()
        !reloadable.isPresent()

        invoked == true
        consumedValue == null
    }
}
