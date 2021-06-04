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

package com.github.tsc4j.spring.app

import com.github.tsc4j.spring.Tsc4jHealthIndicator
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import spock.lang.Unroll

@Unroll
@Slf4j
@SpringBootTest(properties = [
    'tsc4j.spring.health.enabled=false'
])
class Tsc4jHealthIndicatorDisabledSpec extends SpringSpec {
    @Autowired
    ApplicationContext appCtx

    def "should not be available in the context if disabled"() {
        when:
        def indicator = appCtx.getBean(Tsc4jHealthIndicator)

        then:
        thrown(NoSuchBeanDefinitionException)
        indicator == null
    }
}
