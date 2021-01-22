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

package com.github.tsc4j.credstash

import com.amazonaws.SDKGlobalConfiguration
import com.github.tsc4j.core.AbstractConfigValueProviderSpec
import com.github.tsc4j.core.Tsc4jException
import com.github.tsc4j.core.Tsc4jImplUtils
import com.github.tsc4j.testsupport.TestClock
import com.jessecoyle.JCredStash
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueType
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

import java.time.Duration

@Unroll
@RestoreSystemProperties
class CredstashConfigValueProviderSpec extends AbstractConfigValueProviderSpec {
    static def configStr = """
            {
              impl:         credstash
              name:         topsecret

              table-name:   someTableName
              region:       us-west-8
              cache-ttl:    71m
            }
        """
    static def builderConfig = ConfigFactory.parseString(configStr)

    def "builder() should return builder"() {
        when:
        def builder = CredstashConfigValueProvider.builder()

        then:
        builder != null
        builder.getCacheTtl() == Duration.ofMinutes(15)
    }

    def "should return configured instance"() {
        when:
        def providerOpt = Tsc4jImplUtils.createValueProvider(builderConfig, 1)

        then:
        providerOpt.isPresent()

        when:
        def provider = providerOpt.get()
        def credstash = provider.credstash

        then:
        provider instanceof CredstashConfigValueProvider
        credstash.tableName == "someTableName"

        provider.getCache().name == "[credstash, name=topsecret]"
        provider.getCache().cacheTtl == Duration.ofMinutes(71)
    }

    @RestoreSystemProperties
    def "should create provider for impl: '#impl'"() {
        given:
        def configMap = [impl: impl]
        def config = ConfigFactory.parseMap(configMap)
        setupAwsRegion()

        when:
        def providerOpt = Tsc4jImplUtils.createValueProvider(config, 1)

        then:
        providerOpt.isPresent()

        when:
        def provider = providerOpt.get()

        then:
        provider instanceof CredstashConfigValueProvider

        where:
        impl << [
            "credstash",
            " credstash ",
            " Credstash ",
            "com.github.tsc4j.credstash.CredstashConfigValueProvider",
            "  com.github.tsc4j.credstash.CredstashConfigValueProvider  ",
        ]
    }

    def "should not cache any secrets if cache is disabled "() {
        given:
        def clock = new TestClock()
        def credentialName = "some_fine_credential"
        def credentialValue = "this-is-super-secret"
        CredstashConfigValueProvider provider = builder().setCacheTtl(Duration.ofMinutes(0))
                                                         .setClock(clock)
                                                         .build()

        expect: "should not contain the secret"
        !provider.getFromCache(credentialName).isPresent()

        when: "store secret to cache"
        def result = provider.putToCache(credentialName, credentialValue)

        then:
        result.is(credentialValue)
        !provider.getFromCache(credentialName).isPresent()
    }

    def "should cache secrets for specified amount of time"() {
        given:
        def startTimestamp = System.currentTimeMillis()
        def clock = new TestClock(startTimestamp)
        def cacheFor = Duration.ofMinutes(42)

        def credentialName = "some_fine_credential"
        def credentialValue = "this-is-super-secret"
        CredstashConfigValueProvider provider = builder().setCacheTtl(cacheFor)
                                                         .setClock(clock)
                                                         .build()
        expect: "should not contain the secret"
        !provider.getFromCache(credentialName).isPresent()

        when: "store secret to cache"
        def result = provider.putToCache(credentialName, credentialValue)

        then: "secret should be cached"
        result.is(credentialValue)
        provider.getFromCache(credentialName).isPresent()
        provider.getFromCache(credentialName).get() == credentialValue

        when: "move time forwards for few minutes"
        clock.plus(Duration.ofMinutes(15))

        then: "secret should be cached still"
        provider.getFromCache(credentialName).isPresent()
        provider.getFromCache(credentialName).get() == credentialValue

        when: "move time at exact expiration time"
        clock.setTimestamp(startTimestamp).plus(cacheFor)

        then: "secret should disappear from cache"
        !provider.getFromCache(credentialName).isPresent()

        when: "move time a bit more forward"
        clock.plus(Duration.ofMinutes(1))

        then: "secret should not be cached"
        !provider.getFromCache(credentialName).isPresent()

        when: "move time back to provider genesis"
        clock.setTimestamp(startTimestamp)

        then: "secret should not be cached"
        !provider.getFromCache(credentialName).isPresent()
    }

    @RestoreSystemProperties
    def "should correctly fetch values"() {
        given:
        setupAwsRegion()
        def name = "my_provider"

        and: "setup provider"
        def builder = builder().setName(name)
                               .setAllowMissing(true)
                               .setParallel(parallel)
                               .setCacheTtl(Duration.ofMinutes(5))
        def credstash = Mock(JCredStash)
        def provider = new CredstashConfigValueProvider(builder, credstash)

        and: "setup expected secret values"
        def credNameFooBar = "foo.bar"
        def credValueFooBar = "secret_foo_bar"

        def credNameList = "my.list"
        def credentialValueList = "secret_my_list"

        def credNameNonExistent = "non.existent"
        def notFoundException = new RuntimeException("Secret could not be found")

        def credentialNames = [credNameFooBar, credNameList, credNameNonExistent, null, "   "]

        when: "ask for values, with duplicate names"
        def result = provider.get(credentialNames + credentialNames)

        then:
        1 * credstash.getSecret("foo.bar", [:]) >> credValueFooBar
        1 * credstash.getSecret("my.list", [:]) >> credentialValueList
        1 * credstash.getSecret("non.existent", [:]) >> { throw notFoundException }

        result.size() == 2
        result.get(credNameFooBar).unwrapped() == credValueFooBar
        result.get(credNameList).unwrapped() == credentialValueList

        where:
        parallel << [false, true]
    }

    @RestoreSystemProperties
    def "should throw in case of non-existing value"() {
        given: "setup provider"
        def builder = builder().setCacheTtl(Duration.ofMinutes(5))
        def credstash = Mock(JCredStash)
        def provider = new CredstashConfigValueProvider(builder, credstash)

        and:
        def credName = "non.existent"
        def credstashException = new RuntimeException("Secret could not be found")

        when:
        def result = provider.get([credName])

        then:
        1 * credstash.getSecret(credName, [:]) >> { throw credstashException }

        result == null

        def exception = thrown(Tsc4jException)
        exception.getMessage().contains("Credstash credential doesn't exist:")
        exception.getCause().is(credstashException)
    }

    @RestoreSystemProperties
    def "should NOT throw in case of non-existing value"() {
        given: "setup provider"
        def builder = builder().setAllowMissing(true)
                               .setCacheTtl(Duration.ofMinutes(5))
        def credstash = Mock(JCredStash)
        def provider = new CredstashConfigValueProvider(builder, credstash)

        and:
        def credNameA = "non.existent"
        def credstashException = new RuntimeException("Secret could not be found")
        def credNameB = "foo.bar"
        def credNameC = "bar.baz"
        def credValueC = "some value"

        when:
        def result = provider.get([credNameA, credNameC, credNameB])

        then:
        1 * credstash.getSecret(credNameA, [:]) >> { throw credstashException }
        1 * credstash.getSecret(credNameB, [:]) >> { throw credstashException }
        1 * credstash.getSecret(credNameC, [:]) >> credValueC

        result.size() == 1
        result[credNameC].valueType() == ConfigValueType.STRING
        result[credNameC].unwrapped() == credValueC
    }

    CredstashConfigValueProvider.Builder builder() {
        CredstashConfigValueProvider.builder().setRegion("us-west-1")
    }

    def setupAwsRegion() {
        System.setProperty(SDKGlobalConfiguration.AWS_REGION_SYSTEM_PROPERTY, "us-west-1")
    }

    @Override
    CredstashConfigValueProvider getValueProvider() {
        return builder().build()
    }
}
