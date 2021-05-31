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

package com.github.tsc4j.core.impl

import lombok.extern.slf4j.Slf4j
import spock.lang.Specification
import spock.lang.Unroll

@Slf4j
@Unroll
class RunnablesSpec extends Specification {
    def "safeRun() should return expected result"() {
        when:
        def result = Runnables.safeRun(runnable)

        then:
        result == expected

        where:
        runnable                                 | expected
        null                                     | false
        ({ 42 })                                 | true
        ({ throw new RuntimeException("boom") }) | false
    }

    def "safeRunnable() should wrap original runnable"() {
        given:
        def invoked = 0
        def myrunnable = {
            invoked++
            if (exception) {
                throw exception
            }
        } as Runnable

        and:
        def r = Runnables.safeRunnable(myrunnable)

        expect:
        invoked == 0

        when:
        r.run()

        then:
        noExceptionThrown()
        invoked == 1

        where:
        exception << [null, new RuntimeException("boom")]
    }
}
