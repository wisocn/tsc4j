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

package com.github.tsc4j.core.creation

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import groovy.transform.ToString
import groovy.util.logging.Slf4j
import org.slf4j.LoggerFactory
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

@Slf4j
@Unroll
class InstanceCreatorsSpec extends Specification {
    def cfgStr = '''
instances: [
    # empty config, should be ignored
    {},

    # bad config, without implementation, should be ignored
    {
      impl:             "   "
    }
    
    # disabled config, should be ignored
    {
        implementation: bar
        enabled:        false
    }
    
    # enabled config
    {
        impl:           " generic-impl"
        some-int:       11
        some-dur:       42s
    }

    {
        implementation: b
    }

    {
        implementation: aa
        enabled:        true
    }
    
    {
        impl: fault-a
        enabled:        false
    }
]
    '''
    def configs = ConfigFactory.parseString(cfgStr).getConfigList('instances')

    def "instanceCreator() should throw IAE for bad implementation name: #implName"() {
        when:
        def result = InstanceCreators.instanceCreator(CoolServiceCreator, implName)

        then:
        thrown(IllegalArgumentException)
        result == null

        where:
        implName << ['', ' ', "\t \n"]
    }

    def "instanceCreator() should throw IAE for bad creator types: #clazz"() {
        when:
        def result = InstanceCreators.instanceCreator(clazz, 'foo')

        then:
        thrown(IllegalArgumentException)
        result == null

        where:
        clazz << [String, List, AbstractList]
    }

    def "instanceCreator() should return empty result for: #implName"() {
        expect:
        !InstanceCreators.instanceCreator(CoolServiceCreator, implName).isPresent()

        where:
        implName << [
            'non-existing-impl',

            // builders that throw exception while class initialization
            'faulty-a', 'faulty-aa', 'faulty-aa-2',

        ]
    }

    def "instanceCreator() should return result for implementation: #implName"() {
        expect:
        InstanceCreators.instanceCreator(CoolServiceCreator, implName).isPresent()

        where:
        implName << [
            // A
            'A', 'a', 'aa', 'CoolServiceA', 'md.your.utils.creation.CoolServiceA',

            // B
            'B', 'b', 'bb', 'CoolServiceB', 'md.your.utils.creation.CoolServiceB',

            // C
            'C', 'c', 'cc', 'CoolServiceA', 'md.your.utils.creation.CoolServiceC',

            // some custom alias
            'generic-impl',

            // builders that throw while initializing the actual instance
            'faulty-b', 'faulty-bb', 'faulty-bb-2',
        ]
    }

    def "should return expected instance for #implName: #clazz"() {
        when:
        def creator = InstanceCreators.instanceCreator(CoolServiceCreator, implName).get()

        then:
        creator.getClass() == clazz

        where:
        implName                     | clazz
        'A'                          | CoolServiceA.Builder
        '  A '                       | CoolServiceA.Builder
        'a'                          | CoolServiceA.Builder
        '  aa'                       | CoolServiceA.Builder
        CoolServiceA.getName()       | CoolServiceA.Builder
        CoolServiceA.getSimpleName() | CoolServiceA.Builder

        'B'                          | CoolServiceB.Builder
        '  B '                       | CoolServiceB.Builder
        'b'                          | CoolServiceB.Builder
        ' bb '                       | CoolServiceB.Builder
        CoolServiceB.getName()       | CoolServiceB.Builder
        CoolServiceB.getSimpleName() | CoolServiceB.Builder

        'C'                          | CoolServiceC.Builder
        '  C '                       | CoolServiceC.Builder
        'c'                          | CoolServiceC.Builder
        ' cc '                       | CoolServiceC.Builder
        CoolServiceC.getName()       | CoolServiceC.Builder
        CoolServiceC.getSimpleName() | CoolServiceC.Builder

        // 'generic-impl` is supported by two implementations, but C-one is preferred via Ordered interface
        'generic-impl'               | CoolServiceC.Builder
        ' GENERIC-IMPL '             | CoolServiceC.Builder
        ' geNERic-Impl'              | CoolServiceC.Builder
    }

    def "loadInstanceCreator() should return instance: #implName"() {
        expect:
        InstanceCreators.loadInstanceCreator(CoolServiceCreator, implName) != null

        where:
        implName << [
            // A
            'A', 'a', 'aa', 'CoolServiceA', 'md.your.utils.creation.CoolServiceA',

            // B
            'B', 'b', 'bb', 'CoolServiceB', 'md.your.utils.creation.CoolServiceB',

            // C
            'C', 'c', 'cc', 'CoolServiceA', 'md.your.utils.creation.CoolServiceC',

            // some custom alias
            'generic-impl',

            // builders that throw while initializing the actual instance
            'faulty-b', 'faulty-bb', 'faulty-bb-2',
        ]
    }

    def "create() should return empty result for disabled instances"() {
        given:
        def config = ConfigFactory.parseString("""
            impl:       $implName
            enabled:    false
        """)

        expect:
        !InstanceCreators.create(CoolServiceCreator, config).isPresent()

        where:
        implName << ['generic-impl', 'a', 'B', 'cc']
    }

    def "create() should return empty result for undefined implementations"() {
        given:
        def config = ConfigFactory.parseString("""
            impl:       "$implName"
            enabled:    true
        """)

        expect:
        !InstanceCreators.create(CoolServiceCreator, config).isPresent()

        where:
        implName << ['', ' ', '   ']
    }

    def "create() should return configured instance"() {
        given:
        def config = ConfigFactory.parseString("""
            impl:       generic-impl
            some-int:   41
            some-dur:   6667ms
        """)

        when:
        def result = InstanceCreators.create(CoolServiceCreator, config)

        then:
        result.isPresent()
        result.get() instanceof CoolServiceC

        with((CoolServiceC) result.get()) {
            someInt == 41
            someDur.toMillis() == 6667
        }
    }

    def "create() should throw for impl: #implName"() {
        given:
        def config = ConfigFactory.parseString("""
            impl:       $implName
            some-int:   41
        """)

        when:
        def result = InstanceCreators.create(CoolServiceCreator, config)

        then:
        def exception = thrown(RuntimeException)

        exception.getCause().getClass() == expectedExceptionType
        result == null

        where:
        implName    | expectedExceptionType
        'faulty-a'  | IllegalArgumentException
        'faulty-bb' | IllegalStateException
        'faulty-c'  | NullPointerException
    }

    def "create(list) should create correctly configured instances"() {
        when:
        def instances = InstanceCreators.create(CoolServiceCreator, configs)

        then:
        instances.size() == 3

        instances[0] instanceof CoolServiceC
        def svc = (CoolServiceC) instances[0]
        svc.someInt == 11
        svc.someDur == Duration.ofSeconds(42)

        instances[1] instanceof CoolServiceB

        instances[2] instanceof CoolServiceA
    }

    def "create(list) should throw for badly configured instances: #implName"() {
        given:
        def config = ConfigFactory.parseString("""
        instances: [
            { impl: generic-impl        }
            { impl: $implName           }
            { impl: aa                  }       
        ]
        """)
        def configs = config.getConfigList('instances')

        when:
        def instances = InstanceCreators.create(CoolServiceCreator, configs)
        log.info("instances: $instances")

        then:
        def exception = thrown(RuntimeException)
        exception.getMessage().contains("(implementation: $implName, config ")
        instances == null

        where:
        implName << ['faulty-a', 'faulty-b', 'faulty-c', 'non-existent']
    }
}

trait CoolService implements Closeable {
    String generateString() {
        getClass().getName()
    }

    @Override
    void close() {
        LoggerFactory.getLogger(getClass()).warn("closing: {}", this)
    }
}

interface CoolServiceCreator extends InstanceCreator<CoolService> {
}

@Slf4j
class FaultyServiceA implements CoolService {
    static class Builder extends AbstractInstanceCreator<CoolService, Builder> implements CoolServiceCreator, Closeable {
        static {
            // try to load class that does not exist
            Class.forName("foo.bar.baz.NonExistingClass")
        }

        @Override
        String type() {
            'faulty-a'
        }

        @Override
        Set<String> typeAliases() {
            ['faulty-aa', 'faulty-aa-2']
        }

        @Override
        String description() {
            ""
        }

        @Override
        Class<? extends CoolService> creates() {
            FaultyServiceA
        }

        @Override
        void withConfig(Config config) {}

        @Override
        CoolService build() {
            new FaultyServiceA()
        }

        @Override
        void close() {
            log.warn("closing: {}", this)
        }
    }
}

@Slf4j
class FaultyServiceB implements CoolService {
    static class Builder extends AbstractInstanceCreator<CoolService, Builder> implements CoolServiceCreator, Closeable {
        @Override
        String type() {
            'faulty-b'
        }

        @Override
        Set<String> typeAliases() {
            ['faulty-bb', 'faulty-bb-2']
        }

        @Override
        String description() {
            ""
        }

        @Override
        Class<? extends CoolService> creates() {
            FaultyServiceB
        }

        @Override
        void withConfig(Config config) {}

        @Override
        CoolService build() {
            if (1 == 1) {
                throw new IllegalStateException("thou shall not instantiate")
            }
        }

        @Override
        void close() {
            log.warn("closing: {}", this)
        }
    }
}

@Slf4j
class FaultyServiceC implements CoolService {
    static class Builder extends AbstractInstanceCreator<CoolService, Builder> implements CoolServiceCreator, Closeable {
        @Override
        String type() {
            return "faulty-c"
        }

        @Override
        String description() {
            ""
        }

        @Override
        Class<? extends CoolService> creates() {
            FaultyServiceC
        }

        @Override
        void withConfig(Config config) {}

        @Override
        CoolService build() {
            // builder returns null!
            null
        }

        @Override
        void close() {
            log.warn("closing: {}", this)
        }
    }
}

@Slf4j
@ToString
class CoolServiceA implements CoolService {
    CoolServiceA(Builder b) {
        b.checkState()
    }

    //static class Builder implements CoolServiceCreator<Builder>, Closeable {
    static class Builder extends AbstractInstanceCreator<CoolService, Builder> implements CoolServiceCreator, Closeable {
        @Override
        String type() {
            "A"
        }

        @Override
        Set<String> typeAliases() {
            ['aa'] as Set
        }

        @Override
        String description() {
            ""
        }

        @Override
        Class<? extends CoolService> creates() {
            CoolServiceA
        }

        @Override
        void withConfig(Config config) {}

        @Override
        CoolService build() {
            new CoolServiceA(this)
        }

        @Override
        void close() {
            log.warn("closing: {}", this)
        }
    }
}

@Slf4j
@ToString
class CoolServiceB implements CoolService {
    CoolServiceB(Builder b) {
        b.checkState()
    }

    static class Builder extends AbstractInstanceCreator<CoolService, Builder> implements CoolServiceCreator {
        @Override
        String type() {
            "B"
        }

        @Override
        Set<String> typeAliases() {
            ['generic-impl', 'bb'] as Set
        }

        @Override
        String description() {
            ""
        }

        @Override
        Class<? extends CoolService> creates() {
            CoolServiceB
        }


        @Override
        void withConfig(Config config) {}

        @Override
        CoolService build() {
            new CoolServiceB(this)
        }
    }
}

@ToString
class CoolServiceC implements CoolService {
    final int someInt
    final Duration someDur

    CoolServiceC(Builder b) {
        b.checkState()
        this.someInt = b.someInt
        this.someDur = b.someDur
    }

    static class Builder extends AbstractInstanceCreator<CoolService, Builder> implements CoolServiceCreator {
        def someInt = 6
        def someDur = Duration.ofSeconds(2)

        @Override
        String type() {
            "C"
        }

        @Override
        Set<String> typeAliases() {
            ['generic-impl', 'cc'] as Set
        }

        @Override
        String description() {
            ""
        }

        @Override
        Class<? extends CoolService> creates() {
            CoolServiceC
        }

        @Override
        void withConfig(Config config) {
            cfgInt(config, 'some-int').ifPresent({ this.someInt = it })
            cfgDuration(config, 'some-dur').ifPresent({ this.someDur = it })
            getThis()
        }

        @Override
        Builder checkState() {
            if (someInt < 1) {
                throw new IllegalStateException("some integer is too low: $someInt")
            }
            if (someDur.toMillis() < 1000) {
                throw new IllegalStateException("some duration is too short: $someDur")
            }
            getThis()
        }

        @Override
        int getOrder() {
            -1000
        }

        @Override
        CoolService build() {
            new CoolServiceC(this)
        }
    }
}
