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

package com.github.tsc4j.cli

import groovy.util.logging.Slf4j
import spock.lang.Specification
import spock.lang.Unroll

@Slf4j
@Unroll
class AbstractCommandSpec extends Specification {
    def "ordering should work"() {
        given:
        def cmdA = new MyCommand(groups[0], names[0], orders[0])
        def cmdB = new MyCommand(groups[1], names[1], orders[1])
        def cmdC = new MyCommand(groups[2], names[2], orders[2])
        def cmdList = [cmdA, cmdB, cmdC]

        when:
        def sorted = cmdList.collect().sort()

        log.info("orig:   $cmdList")
        log.info("sorted: $sorted")

        then:
        !sorted.is(cmdList)

        sorted[0].is(cmdList[expected[0]])
        sorted[1].is(cmdList[expected[1]])
        sorted[2].is(cmdList[expected[2]])

        where:
        groups          | names           | orders    | expected
        ['A', 'A', 'A'] | ['a', 'b', 'c'] | [3, 1, 2] | [1, 2, 0]
        ['A', 'A', 'A'] | ['b', 'a', 'c'] | [0, 0, 0] | [1, 0, 2]
        ['C', 'A', 'B'] | ['a', 'a', 'a'] | [0, 0, 0] | [1, 2, 0]
        ['C', 'A', 'A'] | ['a', 'b', 'a'] | [0, 0, 0] | [2, 1, 0]
    }

    static class MyCommand extends AbstractCommand {
        String group
        String name
        int order

        MyCommand(String group = '', String name = '', order = 0) {
            this.group = group
            this.name = name
            this.order = order
        }

        @Override
        protected int doCall() {
            0
        }

        @Override
        String getGroup() {
            group
        }

        @Override
        String getName() {
            name
        }

        @Override
        int getOrder() {
            order
        }

        @Override
        String toString() {
            "${getClass().getSimpleName()}($group/$name/$order)"
        }
    }
}
