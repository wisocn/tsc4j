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

package com.github.tsc4j.aws.common

import spock.lang.Ignore
import spock.lang.Specification

class AwsCliCommandSpec extends Specification {
    def "command should contain defaults before running"() {
        given:
        def cmd = new MyCommand()

        when:
        def awsConfig = cmd.getAwsConfig()

        then:
        with(awsConfig) {
            getAccessKeyId() == null
            getSecretAccessKey() == null
            getRegion() == null
        }
    }

    @Ignore('''
            groovy.lang.ReadOnlyPropertyException: Cannot set readonly property: accessKeyId for
            class: com.github.tsc4j.aws.common.AwsCliCommandSpec$MyCommand
    ''')
    def "applied properties should reflect in constructed aws config"() {
        given:
        def accessKeyId = 'foo'
        def secretAccessKey = 'bar'
        def region = 'us-west-8'

        def cmd = new MyCommand()

        when: "set properties and ask for aws config"
        cmd.accessKeyId = accessKeyId
        cmd.secretAccessKey = secretAccessKey
        cmd.region = region

        def awsConfig = cmd.getAwsConfig()

        then:
        with(awsConfig) {
            getAccessKeyId() == accessKeyId
            getSecretAccessKey() == secretAccessKey
            getRegion() == region
        }
    }

    void setField(Object instance, String name, Object value) {
        def clazz = instance.getClass()
        def field = clazz.getField(name)
        //def field = clazz.getDeclaredField(name)
        field.setAccessible(true)
        field.set(instance, value)
    }

    class MyCommand extends AwsCliCommand {
        int numCalled = 0

        @Override
        String getName() {
            return "my-command"
        }

        @Override
        int doCall() {
            numCalled++
        }
    }
}
