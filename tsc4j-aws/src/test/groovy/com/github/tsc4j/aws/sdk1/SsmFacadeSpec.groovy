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

package com.github.tsc4j.aws.sdk1

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement
import com.github.tsc4j.core.Tsc4jException
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
@IgnoreIf({ env['CI'] })
class SsmFacadeSpec extends Specification {
    static def exception = new RuntimeException("bang!")

    @Shared
    @AutoCleanup("shutdown")
    def ssm = AwsTestEnv.setupSSM()

    SsmFacade createFacade(AWSSimpleSystemsManagement ssm) {
        new SsmFacade(ssm, "foo.bar", true, false)
    }

    def "should correctly fetch parameters by path"() {
        given:
        def facade = createFacade(ssm)

        when:
        def params = facade.fetchByPath(path).collect { it.getValue() }

        then:
        params == expected

        where:
        path            | expected
        '/non-existent' | []
        '/a'            | ['a.x', 'a.y,a,b,c', 'a.z']
        '/b'            | ['42']
        '/b/c'          | ['42']
        '/b/c/d'        | []
        '/b/c/d/'       | []
    }

    def "should throw tsc4j exception in case of errors"() {
        given:
        def ssm = Mock(AWSSimpleSystemsManagement)
        def facade = new SsmFacade(ssm, "foo.bar", true, false)

        when: "execute action on a facade"
        def result = action.call(facade)

        then:
        ssm._ >> { throw exception }

        def thrown = thrown(Exception)
        thrown instanceof Tsc4jException
        thrown.getCause().is(exception)

        result == null

        where:
        action << [
            { SsmFacade it -> it.list() }
        ]
    }
}
