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

package com.github.tsc4j.api

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigMemorySize
import com.typesafe.config.ConfigValueFactory
import groovy.util.logging.Slf4j
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.time.Period
import java.util.function.Consumer

@Slf4j
@Unroll
class WithConfigSpec extends Specification {
    static def cfgStr = '''
    foo: bar
    
    sub.category {
      a-boolean:        true
      a-number:         42
      anInteger:        43
      aDouble:          3.14
      
      buffer-size:      3g
      
      somePeriod:       2d
      some-duration:    16m
    }
    '''

    @Shared
    def config = ConfigFactory.parseString(cfgStr)

    @Shared
    def subConfig = config.getConfig('sub.category')

    @Shared
    def instance = new WithConfig() {
        @Override
        void withConfig(Config config) {
        }
    }

    def "cfgXXX() methods should throw ConfigException for a wrong type"() {
        when:
        def opt = closure.call(instance)

        then:
        thrown(ConfigException)
        opt == null

        where:
        closure << [
            { it.cfgExtract(config, 'foo', { cfg, path -> cfg.getBoolean(path) }) },
            { it.cfgBoolean(config, 'foo') },
            { it.cfgNumber(config, 'foo') },
            { it.cfgInt(config, 'foo') },
            { it.cfgLong(config, 'foo') },
            { it.cfgDouble(config, 'foo') },
            { it.cfgString(config, 'sub.category') },
            { it.cfgConfigObject(config, 'foo') },
            { it.cfgConfig(config, 'foo') },
            { it.cfgBytes(config, 'foo') },
            { it.cfgMemorySize(config, 'foo') },
            { it.cfgDuration(config, 'foo') },
            { it.cfgPeriod(config, 'foo') },
            { it.cfgTemporalAmount(config, 'foo') },
        ]
    }


    def "cfgXXX() should return empty optional for nonexisting config paths"() {
        expect:
        !closure.call(instance).isPresent()

        where:
        closure << [
            { it.cfgExtract(config, 'non-existent', { cfg, path -> cfg.getBoolean(path) }) },
            { it.cfgBoolean(config, 'non-existent') },
            { it.cfgNumber(config, 'non-existent') },
            { it.cfgInt(config, 'non-existent') },
            { it.cfgLong(config, 'non-existent') },
            { it.cfgDouble(config, 'non-existent') },
            { it.cfgString(config, 'non-existent') },
            { it.cfgConfigObject(config, 'non-existent') },
            { it.cfgConfig(config, 'non-existent') },
            { it.cfgBytes(config, 'non-existent') },
            { it.cfgMemorySize(config, 'non-existent') },
            { it.cfgDuration(config, 'non-existent') },
            { it.cfgPeriod(config, 'non-existent') },
            { it.cfgTemporalAmount(config, 'non-existent') },
            { it.cfgConfigValue(config, 'non-existent') },
            { it.cfgAnyRef(config, 'non-existent') },
        ]
    }

    def "cfgBoolean should return expected result"() {
        expect:
        instance.cfgBoolean(subConfig, 'a-boolean').get() == true
    }

    def "cfgXXX() should return expected value"() {
        expect:
        closure.call(instance).get() == expected

        where:
        closure                                                               | expected
        { WithConfig it -> it.cfgBoolean(subConfig, 'a-boolean') }            | true

        { WithConfig it -> it.cfgNumber(subConfig, 'a-number') }              | 42
        { WithConfig it -> it.cfgNumber(subConfig, 'a-double') }              | 3.14
        { WithConfig it -> it.cfgNumber(subConfig, 'aDouble') }               | 3.14

        { WithConfig it -> it.cfgInt(subConfig, 'a-number') }                 | 42
        { WithConfig it -> it.cfgInt(subConfig, 'an-integer') }               | 43
        { WithConfig it -> it.cfgInt(subConfig, 'anInteger') }                | 43

        { WithConfig it -> it.cfgLong(subConfig, 'a-number') }                | 42
        { WithConfig it -> it.cfgLong(subConfig, 'an-integer') }              | 43
        { WithConfig it -> it.cfgLong(subConfig, 'anInteger') }               | 43

        { WithConfig it -> it.cfgDouble(subConfig, 'a-number') }              | 42
        { WithConfig it -> it.cfgDouble(subConfig, 'an-integer') }            | 43
        { WithConfig it -> it.cfgDouble(subConfig, 'anInteger') }             | 43
        { WithConfig it -> it.cfgDouble(subConfig, 'a-double') }              | 3.14
        { WithConfig it -> it.cfgDouble(subConfig, 'aDouble') }               | 3.14

        { WithConfig it -> it.cfgString(subConfig, 'a-number') }              | '42'
        { WithConfig it -> it.cfgString(subConfig, 'a-double') }              | '3.14'

        { WithConfig it -> it.cfgConfigObject(config, 'sub.category') }       | subConfig.root()

        { WithConfig it -> it.cfgConfig(config, 'sub.category') }             | subConfig

        { WithConfig it -> it.cfgAnyRef(config, 'foo') }                      | 'bar'

        { WithConfig it -> it.cfgConfigValue(config, 'foo') }                 | ConfigValueFactory.fromAnyRef('bar')

        { WithConfig it -> it.cfgBytes(subConfig, 'buffer-size') }            | ((long) 3 * 1024 * 1024 * 1024)
        { WithConfig it -> it.cfgMemorySize(subConfig, 'buffer-size') }       | ConfigMemorySize.ofBytes(((long) 3 * 1024 * 1024 * 1024))

        { WithConfig it -> it.cfgDuration(subConfig, 'some-duration') }       | Duration.ofMinutes(16)
        { WithConfig it -> it.cfgDuration(subConfig, 'some-period') }         | Duration.ofDays(2)
        { WithConfig it -> it.cfgDuration(subConfig, 'somePeriod') }          | Duration.ofDays(2)

        { WithConfig it -> it.cfgPeriod(subConfig, 'some-duration') }         | Period.parse('P16M')
        { WithConfig it -> it.cfgPeriod(subConfig, 'some-period') }           | Period.parse('P2D')
        { WithConfig it -> it.cfgPeriod(subConfig, 'somePeriod') }            | Period.parse('P2D')

        { WithConfig it -> it.cfgTemporalAmount(subConfig, 'some-duration') } | Duration.ofMinutes(16)
        { WithConfig it -> it.cfgTemporalAmount(subConfig, 'somePeriod') }    | Duration.ofDays(2)
        { WithConfig it -> it.cfgTemporalAmount(subConfig, 'some-period') }   | Duration.ofDays(2)
    }

    def "cfgXXX() should invoke consumer and return expected value"() {
        given:
        def consumerCalledWith = null
        def numCalls = 0
        def consumer = {
            numCalls++
            consumerCalledWith = it
        }

        when:
        def opt = closure.call(instance, consumer)
        log.info("got: {}", opt)

        then:
        with(opt) {
            isPresent()
            get() == expected
        }

        numCalls == 1
        consumerCalledWith == expected

        where:
        closure                                                                              | expected
        { WithConfig it, Consumer c -> it.cfgBoolean(subConfig, 'a-boolean', c) }            | true

        { WithConfig it, Consumer c -> it.cfgNumber(subConfig, 'a-number', c) }              | 42
        { WithConfig it, Consumer c -> it.cfgNumber(subConfig, 'a-double', c) }              | 3.14
        { WithConfig it, Consumer c -> it.cfgNumber(subConfig, 'aDouble', c) }               | 3.14

        { WithConfig it, Consumer c -> it.cfgInt(subConfig, 'a-number', c) }                 | 42
        { WithConfig it, Consumer c -> it.cfgInt(subConfig, 'an-integer', c) }               | 43
        { WithConfig it, Consumer c -> it.cfgInt(subConfig, 'anInteger', c) }                | 43

        { WithConfig it, Consumer c -> it.cfgLong(subConfig, 'a-number', c) }                | 42
        { WithConfig it, Consumer c -> it.cfgLong(subConfig, 'an-integer', c) }              | 43
        { WithConfig it, Consumer c -> it.cfgLong(subConfig, 'anInteger', c) }               | 43

        { WithConfig it, Consumer c -> it.cfgDouble(subConfig, 'a-number', c) }              | 42
        { WithConfig it, Consumer c -> it.cfgDouble(subConfig, 'an-integer', c) }            | 43
        { WithConfig it, Consumer c -> it.cfgDouble(subConfig, 'anInteger', c) }             | 43
        { WithConfig it, Consumer c -> it.cfgDouble(subConfig, 'a-double', c) }              | 3.14
        { WithConfig it, Consumer c -> it.cfgDouble(subConfig, 'aDouble', c) }               | 3.14

        { WithConfig it, Consumer c -> it.cfgString(subConfig, 'a-number', c) }              | '42'
        { WithConfig it, Consumer c -> it.cfgString(subConfig, 'a-double', c) }              | '3.14'

        { WithConfig it, Consumer c -> it.cfgConfigObject(config, 'sub.category', c) }       | subConfig.root()

        { WithConfig it, Consumer c -> it.cfgConfig(config, 'sub.category', c) }             | subConfig

        { WithConfig it, Consumer c -> it.cfgAnyRef(config, 'foo', c) }                      | 'bar'

        { WithConfig it, Consumer c -> it.cfgConfigValue(config, 'foo', c) }                 | ConfigValueFactory.fromAnyRef('bar')

        { WithConfig it, Consumer c -> it.cfgBytes(subConfig, 'buffer-size', c) }            | ((long) 3 * 1024 * 1024 * 1024)
        { WithConfig it, Consumer c -> it.cfgMemorySize(subConfig, 'buffer-size', c) }       | ConfigMemorySize.ofBytes(((long) 3 * 1024 * 1024 * 1024))

        { WithConfig it, Consumer c -> it.cfgDuration(subConfig, 'some-duration', c) }       | Duration.ofMinutes(16)
        { WithConfig it, Consumer c -> it.cfgDuration(subConfig, 'some-period', c) }         | Duration.ofDays(2)
        { WithConfig it, Consumer c -> it.cfgDuration(subConfig, 'somePeriod', c) }          | Duration.ofDays(2)

        { WithConfig it, Consumer c -> it.cfgPeriod(subConfig, 'some-duration', c) }         | Period.parse('P16M')
        { WithConfig it, Consumer c -> it.cfgPeriod(subConfig, 'some-period', c) }           | Period.parse('P2D')
        { WithConfig it, Consumer c -> it.cfgPeriod(subConfig, 'somePeriod', c) }            | Period.parse('P2D')

        { WithConfig it, Consumer c -> it.cfgTemporalAmount(subConfig, 'some-duration', c) } | Duration.ofMinutes(16)
        { WithConfig it, Consumer c -> it.cfgTemporalAmount(subConfig, 'somePeriod', c) }    | Duration.ofDays(2)
        { WithConfig it, Consumer c -> it.cfgTemporalAmount(subConfig, 'some-period', c) }   | Duration.ofDays(2)
    }
}
