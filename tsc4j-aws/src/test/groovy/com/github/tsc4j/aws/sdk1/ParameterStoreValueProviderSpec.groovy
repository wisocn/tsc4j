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

import groovy.util.logging.Slf4j
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

@Slf4j
@Stepwise
@Ignore
class ParameterStoreValueProviderSpec extends Specification {
    @Shared
    def paramNames = []

    @Shared
    def provider = ParameterStoreValueProvider.builder()
                                              .setRegion("us-west-2")
                                              .build()

    def "should list all parameters"() {
        when:
        def names = provider.names()

        def sb = new StringBuilder("received aws SSM parameter names:\n")
        names.each { sb.append("  $it\n") }
        log.info(sb.toString())

        then:
        names.size() > 30

        when: "assign param names"
        paramNames = names

        then:
        true
    }

    def "should work"() {
        when:

        def ts = System.currentTimeMillis()
        def results = provider.get(paramNames)
        def duration = System.currentTimeMillis() - ts

        log.info("results: {} (duration: {} msec)", results.size(), duration)
        results.each { k, v -> log.info("  $k : ${v.unwrapped()}") }

        then:
        true
    }
}
