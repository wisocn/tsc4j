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

package com.github.tsc4j.examples.boot.twodotzero

import com.github.tsc4j.api.Reloadable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import spock.lang.Specification

@ActiveProfiles("test")
@SpringBootTest(classes = Application)
class ApplicationSpec extends Specification {
    @Autowired
    ApplicationContext ctx

    @Autowired
    Reloadable<AppConfig> reloadable

    @Value('${my.config.foo}')
    String fooVar

    def "context should be initalized"() {
        expect:
        ctx != null

        reloadable.isPresent()
        reloadable.get().getFoo() == "something interesting"
        reloadable.get().getStringList() == ["a", "b", "b", "a"]
        reloadable.get().getStringSet() == ["a", "b"] as Set

        fooVar == "something interesting"
    }
}
