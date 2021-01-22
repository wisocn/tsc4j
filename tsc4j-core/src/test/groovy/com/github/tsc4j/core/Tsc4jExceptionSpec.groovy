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

import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class Tsc4jExceptionSpec extends Specification {
    def "2-args constructor should create expected exception"() {
        given:
        def msg = "boom!"
        def cause = new IOException("foo")

        when:
        def exception = new Tsc4jException(msg, cause)

        then:
        exception.getMessage().is(msg)
        exception.getCause().is(cause)
    }

    def "of() should produce expected result"() {
        given:
        def msgFmt = 'a b c %s %d: %%s'
        def string = "hahaha"
        def number = 42

        and: "setup expected message"
        def expectedMessage = String.format(msgFmt, string, number).replaceFirst('%s', '')
        if (cause.getMessage() == null) {
            expectedMessage = expectedMessage + cause.toString()
        } else {
            expectedMessage = expectedMessage + cause.getMessage()
        }

        when:
        def result = Tsc4jException.of(msgFmt, cause, string, number)

        then:
        result.getMessage() == expectedMessage
        result.getCause().is(cause)
        result.getStackTrace() == [] // stacktrace should be empty
        result.getSuppressed() == [] // suppressed exceptions should be empty

        where:
        cause << [new IOException(),
                  new IOException(new RuntimeException("bar")),
                  new IOException(""),
                  new IOException("foo"),
                  new IOException("foo", new RuntimeException("bar"))
        ]
    }
}
