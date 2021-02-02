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

package com.github.tsc4j.core.impl

import com.typesafe.config.ConfigValueFactory
import groovy.util.logging.Slf4j
import spock.lang.Specification
import spock.lang.Unroll

import static com.typesafe.config.ConfigValueType.LIST
import static com.typesafe.config.ConfigValueType.OBJECT
import static com.typesafe.config.ConfigValueType.STRING

@Slf4j
@Unroll
class VarStrSpec extends Specification {
    def "constructor should create empty instance for '#str'"() {
        when:
        def varStr = new VarStr(str)
        log.info("created: '{}'", varStr)

        then:
        varStr.count() == 0
        varStr.isEmpty()
        !varStr.isPresent()

        when:
        def num = 0
        varStr.scan({ num++ })

        then: "consumer should not be invoked"
        num == 0

        where:
        str << [
            "",
            "  ",
            " %{}",
            " %{ }",
            " %{",
            " %{ } %{",
            " %{ } %{  ",
        ]
    }

    def "first() should throw if str doesn't contain any magic vars"() {
        when:
        def varstr = new VarStr(str)
        def first = varstr.first()

        then:
        thrown(IllegalStateException)
        first == null

        varstr.isEmpty()
        !varstr.isPresent()
        varstr.count() == 0

        where:
        str << ['', '%{', '%{}', '{}', '$foo.bar', 'foo.bar']
    }

    def "first() should return first magic variable"() {
        expect:
        new VarStr(str).first() == expected

        where:
        str                       | expected
        '%{ foo.bar }'            | 'foo.bar'
        ' %{foo.bar} %{a.b} blah' | 'foo.bar'
    }

    def "constructor should correctly create non-empty instance"() {
        when:
        def varStr = new VarStr(str)

        then:
        varStr.isPresent()
        !varStr.isEmpty()
        varStr.count() == expected.size()

        when:
        def acc = []
        varStr.scan({ acc.add(it) })

        then:
        acc == expected

        where:
        str                                                            | expected
        // TODO: re-enable the following when `%` becomes mandatory
        //'${foo.bar} %{bar.baz} {a.b} %{}, %{aaa| name =aaa | cccc }'  | ['bar.baz', 'aaa| name =aaa | cccc']
        //' ${foo.bar} %{bar.baz} {a.b} %{}, %{aaa| name =aaa | cccc }' | ['bar.baz', 'aaa| name =aaa | cccc']
        '${foo.bar} %{bar.baz} ${a.b} %{}, %{aaa| name =aaa | cccc }'  | ['bar.baz', 'aaa| name =aaa | cccc']
        ' ${foo.bar} %{bar.baz} ${a.b} %{}, %{aaa| name =aaa | cccc }' | ['bar.baz', 'aaa| name =aaa | cccc']

        '%{ foo.bar } %{ } %{ a.b} %{}, %{c . d }'                     | ['foo.bar', 'a.b', 'c . d']
        '%{ foo.bar } %{ } ${ a.b} %{}, %{c . d }'                     | ['foo.bar', 'c . d']
    }

    def "scan should invoke consume with correct valuer"() {
        given:
        def acc = []
        def varStr = new VarStr(str)

        when:
        varStr.scan({ acc.add(it) })

        then:
        acc == expected

        where:
        str                                                            | expected
        // TODO: re-enable the following 2 lines when `%` becomes mandatory
        //'${foo.bar} %{bar.baz} {a.b} %{}, %{aaa| name =aaa | cccc }'  | ['bar.baz', 'aaa| name =aaa | cccc']
        //' ${foo.bar} %{bar.baz} {a.b} %{}, %{aaa| name =aaa | cccc }' | ['bar.baz', 'aaa| name =aaa | cccc']
        '${foo.bar} %{bar.baz} ${a.b} %{}, %{aaa| name =aaa | cccc }'  | ['bar.baz', 'aaa| name =aaa | cccc']
        ' ${foo.bar} %{bar.baz} ${a.b} %{}, %{aaa| name =aaa | cccc }' | ['bar.baz', 'aaa| name =aaa | cccc']

        '%{ foo.bar } %{ } %{ a.b} %{}, %{c . d }'                     | ['foo.bar', 'a.b', 'c . d']
        '%{ foo.bar } %{ } ${ a.b} %{}, %{c . d }'                     | ['foo.bar', 'c . d']
    }

    def "replace() should return expected result"() {
        given:
        def transformMap = [
            'foo.bar': 'Fx.Fy',
            'bar.baz': "Bx.By"
        ]
        def transformFunction = { Optional.ofNullable(transformMap.get(it)) }

        when:
        def result = new VarStr(str).replace(transformFunction)
        log.info("result: '$result'")

        then:
        result == expected

        where:
        str                                                  | expected
        ' %{foo.bar}  %{ foo.bar}   %{ foo.bar  } '          | ' Fx.Fy  Fx.Fy   Fx.Fy '
        ' %{foo.bar}  %{ foo.bar}   %{ foo.bar  } %{x.y}'    | ' Fx.Fy  Fx.Fy   Fx.Fy %{x.y}'
        ' %{foo.bar}  %{ bar.baz}   %{ foo.bar  } %{x.y}'    | ' Fx.Fy  Bx.By   Fx.Fy %{x.y}'
        ' %{foo.bar}  %{ bar.baz}   %{ foo.bar  } %{x.y}'    | ' Fx.Fy  Bx.By   Fx.Fy %{x.y}'
        ' %{foo.bar}  %{ bar.baz}   %{ foo.bar  } %{%{x.y} ' | ' Fx.Fy  Bx.By   Fx.Fy %{%{x.y} '
    }

    def "resolve('#str') should return expected result"() {
        given:
        def transformMap = [
            'foo.bar'  : 'Fx.Fy',
            'bar.baz'  : "Bx.By",
            'some.list': ['a', 'b', 'c'],
            'some.obj' : ['a': 'b', 'c': 'd']
        ]
        def transformFunction = { key ->
            log.info("transforming: $key -> ${transformMap.get(key)}")
            Optional.ofNullable(transformMap.get(key))
                    .map {
                        def res = ConfigValueFactory.fromAnyRef(it)
                        log.info("transformed: $key ($it) -> $res")
                        res
                    }
        }

        when:
        def varStr = new VarStr(str)
        def result = varStr.resolve(transformFunction)
        log.info("result: $varStr -> '$result'")

        then:
        result.valueType() == type
        result.unwrapped() == expected

        where:
        str                                                  | type   | expected
        ' %{foo.bar}  %{ foo.bar}   %{ foo.bar  } '          | STRING | ' Fx.Fy  Fx.Fy   Fx.Fy '
        ' %{foo.bar}  %{ foo.bar}   %{ foo.bar  } %{x.y}'    | STRING | ' Fx.Fy  Fx.Fy   Fx.Fy %{x.y}'
        ' %{foo.bar}  %{ bar.baz}   %{ foo.bar  } %{x.y}'    | STRING | ' Fx.Fy  Bx.By   Fx.Fy %{x.y}'
        ' %{foo.bar}  %{ bar.baz}   %{ foo.bar  } %{x.y}'    | STRING | ' Fx.Fy  Bx.By   Fx.Fy %{x.y}'
        ' %{foo.bar}  %{ bar.baz}   %{ foo.bar  } %{%{x.y} ' | STRING | ' Fx.Fy  Bx.By   Fx.Fy %{%{x.y} '

        // list types
        '%{some.list}'                                       | LIST   | ['a', 'b', 'c']
        '%{some.list }'                                      | LIST   | ['a', 'b', 'c']
        '%{ some.list }'                                     | LIST   | ['a', 'b', 'c']
        ' %{ some.list }'                                    | LIST   | ['a', 'b', 'c']
        '%{ some.list } '                                    | LIST   | ['a', 'b', 'c']
        ' %{ some.list } '                                   | LIST   | ['a', 'b', 'c']

        // object types
        '%{some.obj}'                                        | OBJECT | [a: 'b', c: 'd']
        ' %{ some.obj }'                                     | OBJECT | [a: 'b', c: 'd']

        // mixed types
        ' %{foo.bar} %{some.list} '                          | STRING | ' Fx.Fy a,b,c '
        ' %{foo.bar} %{some.list} %{some.obj}'               | STRING | ' Fx.Fy a,b,c {"a":"b","c":"d"}'

        // strings without vars
        ''                                                   | STRING | ''
        ' '                                                  | STRING | ' '
        ' %{}'                                               | STRING | ' %{}'
    }
}
