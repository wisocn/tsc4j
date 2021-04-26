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

import com.github.tsc4j.core.AbstractConfigSource
import com.github.tsc4j.core.CloseableInstance
import com.github.tsc4j.core.ConfigQuery
import com.github.tsc4j.core.ConfigSource
import com.github.tsc4j.core.ConfigSourceBuilder
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import groovy.util.logging.Slf4j
import lombok.NonNull
import org.slf4j.Logger
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
@Slf4j
class AbstractConfigSourceProtectedSpec extends Specification {
    static def appName = "superApp"
    static def datacenterName = "superDatacenter"

    def "getEnvsFromConfigQuery() should return valid result"() {
        given:
        def query = ConfigQuery.builder()
                               .appName(appName)
                               .datacenter(datacenterName)
                               .envs(envs)
                               .build()

        when:
        def result = source().getEnvsFromConfigQuery(query)
        log.info("envs {} -> {}", envs, result)

        then:
        result == expected

        where:
        envs                              | expected
        []                                | AbstractConfigSource.DEFAULT_ENVS
        [null]                            | AbstractConfigSource.DEFAULT_ENVS
        [null, '', ' ', '  ']             | AbstractConfigSource.DEFAULT_ENVS
        ['foo', ' foo  ', ' bar ', 'foo'] | ['foo', 'bar']
    }

    def "interpolateVarStrings() should return correct result: #envs"() {
        given:
        def query = ConfigQuery.builder()
                               .appName(appName)
                               .datacenter(datacenterName)
                               .envs(envs)
                               .build()
        def strings = [
            'common',
            'common/${application}',
            '${env}',
            '${env}/${application}',
            '',
            null,
            ' ', // string containing only space
            ' x ', // string containing spaces, we want them to be retained
            'common/${application}', // this string was already specified, we don't want to be repeated
        ]

        when:
        def result = source().interpolateVarStrings(strings, query)
        log.info("result for {}: {}", query, result)

        then:
        result == expected

        where:
        envs                                             | expected
        []                                               | ['common', "common/$appName", "default", "default/$appName", ' ', ' x ']
        [null, "", "   ", " "]                           | ['common', "common/$appName", "default", "default/$appName", ' ', ' x ']
        ["dev", "", "dev", "  "]                         | ['common', "common/$appName", "dev", "dev/$appName", ' ', ' x ']
        ["dev", "", "dev", "  ", "prod", null, " prod "] | ['common', "common/$appName", "dev", "dev/$appName", ' ', ' x ', "prod", "prod/$appName"]
    }

    def "debugLoadedConfig should always return original instance"() {
        given:
        def config = ConfigFactory.empty()

        expect:
        source().debugLoadedConfig(path, config).is(config)

        where:
        path << [null, "  ", "foo", "bar"]
    }

    def "warnOrThrowOnMissingConfigLocation() should throw if failOnMissing is enabled"() {
        given:
        def location = "someLocation"
        def source = source(false, true, true)

        when:
        source.warnOrThrowOnMissingConfigLocation(location)

        then:
        def exception = thrown(RuntimeException)
        exception.getMessage().contains("location does not exist")
        exception.getMessage().endsWith(": " + location)
    }

    def "warnOrThrowOnMissingConfigLocation() should NOT throw if failOnMissing is disabled"() {
        given:
        def location = "someLocation"
        def source = source(false, warnOnMissing, false)

        and: "assign mock logger "
        def logger = Mock(Logger)
        assignLogger(source, logger)

        when:
        source.warnOrThrowOnMissingConfigLocation(location)

        then:
        noExceptionThrown()

        if (warnOnMissing) {
            1 * logger.warn({ it.contains("location does not exist") }, source, location)
        } else {
            1 * logger.debug({ it.contains("location does not exist") }, source, location)
        }

        where:
        warnOnMissing << [true, false]
    }

    def "debugLoadedConfig() should not log anything if trace is not enabled"() {
        given:
        def config = ConfigFactory.empty()
        def source = source()

        and: "assign mock logger "
        def logger = Mock(Logger)
        assignLogger(source, logger)

        when:
        def result = source.debugLoadedConfig(path, config)

        then:
        logger.isTraceEnabled() >> false
        0 * logger._

        result.is(config)

        where:
        path << [null, "", "  ", "foo"]
    }

    def "debugLoadedConfig() should log using trace"() {
        given:
        def config = ConfigFactory.empty()
        def source = source()

        and: "assign mock logger "
        def logger = Mock(Logger)
        assignLogger(source, logger)

        when:
        def result = source.debugLoadedConfig(path, config)

        then:
        logger.isTraceEnabled() >> true
        if (path == null || path.isEmpty()) {
            1 * logger.trace(_, source, "", true, config)
        } else {
            1 * logger.trace(_, source, "from '$path' ", true, config)
        }

        result.is(config)

        where:
        path << [null, "", "  ", "foo"]
    }

    def assignLogger(AbstractConfigSource source, Logger logger) {
        def logField = CloseableInstance.getDeclaredField("log")
        logField.setAccessible(true)
        logField.set(source, logger)
        source
    }

    def source(boolean allowErrors = false,
               boolean warnOnMissing = true,
               boolean failOnMissing = false) {
        def builder = new ConfigSourceBuilder() {
            @Override
            String type() {
                return null
            }

            @Override
            String description() {
                return null
            }

            @Override
            Class creates() {
                return null
            }

            @Override
            ConfigSource build() {
                return null
            }

            @Override
            int compareTo(Object o) {
                return 0
            }
        }
        builder.setAllowErrors(allowErrors).setWarnOnMissing(warnOnMissing).setFailOnMissing(failOnMissing)

        new AbstractConfigSource(builder) {
            @Override
            protected List<Config> fetchConfigs(@NonNull ConfigQuery query) {
                null
            }

            @Override
            public String getType() {
                return "anonymous"
            }
        }
    }
}
