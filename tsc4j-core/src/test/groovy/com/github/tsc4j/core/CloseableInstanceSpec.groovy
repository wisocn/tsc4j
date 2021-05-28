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

package com.github.tsc4j.core

import groovy.transform.ToString
import groovy.util.logging.Slf4j
import spock.lang.Specification

@Slf4j
class CloseableInstanceSpec extends Specification {
    def "instance should not be closed by default"() {
        expect:
        !closer.isClosed()
        closer.checkClosed()
        closer.numCloses == 0

        where:
        closer << [
            new MyCloser(),
            new MyCloser(warn: false, closeException: null),
            new MyCloser(warn: false, closeException: new RuntimeException()),
            new MyCloser(warn: true, closeException: null),
            new MyCloser(warn: true, closeException: new RuntimeException()),
        ]
    }

    def "checkClosed() should throw  on closed instance"() {
        given:
        def closer = new MyCloser()

        when:
        closer.close()

        then:
        noExceptionThrown()
        closer.isClosed()

        when:
        closer.checkClosed()

        then:
        def ex = thrown(IllegalStateException)
        def msg = ex.getMessage()
        msg.startsWith("Instance is closed")
    }

    def "doClose() should be called only once"() {
        given:
        def closer = new MyCloser()

        when:
        closer.close()

        then:
        noExceptionThrown()
        closer.isClosed()
        closer.numCloses == 1

        when: "try to close it again"
        5.times { closer.close() }

        then:
        closer.isClosed()
        closer.numCloses == 1
    }

    def "close() should re-throw if doClose() throws, but instance should be marked as closed"() {
        given:
        def exception = new RuntimeException("foo")
        def closer = new MyCloser(closeException: exception)

        when:
        closer.close()

        then:
        def ex = thrown(Throwable)
        ex.is(exception)

        closer.isClosed()
        closer.numCloses == 1

        when:
        closer.checkClosed()

        then:
        thrown(IllegalStateException)
    }

    def "close() should not warn about already closed if warnIfAlreadyClosed() says so"() {
        given:
        def closer = new MyCloser(warn: false)

        when:
        5.times { closer.close() }

        then:
        noExceptionThrown()
        closer.isClosed()
    }

    def "close() should warn about already closed if warnIfAlreadyClosed() says so"() {
        given:
        def closer = new MyCloser(warn: true)

        when:
        5.times { closer.close() }

        then:
        noExceptionThrown()
        closer.isClosed()
    }

    @ToString(includePackage = false, includeNames = true)
    static class MyCloser extends CloseableInstance {
        boolean warn
        Throwable closeException

        private int numCloses = 0

        @Override
        protected boolean warnIfAlreadyClosed() {
            return warn
        }

        @Override
        protected void doClose() {
            numCloses++
            if (closeException) {
                throw closeException
            }
        }
    }
}
