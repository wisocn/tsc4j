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

import com.github.tsc4j.core.AbstractConfigSource
import com.github.tsc4j.core.ConfigSourceBuilder
import com.github.tsc4j.core.ConfigSourceCreator
import com.github.tsc4j.core.FilesystemLikeConfigSourceSpec
import com.github.tsc4j.core.creation.InstanceCreators
import groovy.util.logging.Slf4j
import spock.lang.Unroll

@Slf4j
@Unroll
class ClasspathConfigSourceSpec extends FilesystemLikeConfigSourceSpec {
    def "creator should be discoverable for implementation type: #impl"() {
        expect:
        InstanceCreators.loadInstanceCreator(ConfigSourceCreator, impl) instanceof ClasspathConfigSource.Builder

        where:
        impl << [
            'classpath', 'cp', 'ClasspathConfigSource', 'com.github.tsc4j.core.impl.ClasspathConfigSource'
        ]
    }

    def "should not warn on non-existing by default"() {
        expect:
        !ClasspathConfigSource.builder().withPath("/foo").build().isWarnOnMissing()
        !ClasspathConfigSource.defaultBuilder().isWarnOnMissing()
    }

    def "should retrieve all available configs"() {
        given: "setup vars"
        def envName = "foo"
        def prefix = "/bundled_config"
        def builder = builder()
            .withPath(prefix + '/config///${env}')
            .withPath(prefix + 'nonexistent')
            .withPath(prefix + '/' + defaultAppName + '/${env}');

        and:
        def source = builder.build()

        def query = defaultConfigQuery.toBuilder()
                                      .env(envName)
                                      .build()

        when:
        def config = source.get(query)
        log.info("loaded config: {}", config)

        then:
        !config.isEmpty()
        !config.isResolved()

        config.getString("haha.a") == "c"
        config.getString("haha.b") == "d"

        config.getString("sect_aa.aa") == "trololo"
        config.getString("sect_aa.bb") == "bar"
        config.getString("sect_aa.cc") == "haha"

        // this one doesn't exist (unresolved config)
        // !config.getString("sect_aa.java").isEmpty()

        config.getString("sect_zz.x") == "foo"
        config.getString("sect_zz.y") == "bar"
    }

    @Unroll
    def "isDirectory(#path) should return #expected"() {
        expect:
        dummySource().isDirectory(path, null) == expected

        where:
        path                | expected
        //TODO: this one fails when ran together with other test, but succeeds in isolation, investigate why
        //"/"                 | true
        "/nonexistent"      | false
        "/bundled_config"   | true
        "/logback-test.xml" | false
    }

    @Unroll
    def "pathExists(#path) should return #expected"() {
        expect:
        dummySource().pathExists(path, null) == expected

        where:
        path                | expected
        "/"                 | true
        "/nonexistent"      | false
        "/bundled_config"   | true
        "/logback-test.xml" | true
    }

    def "should not allow errors by default"() {
        expect:
        dummySource().allowErrors() == false
    }

    def "should allow errors"() {
        when:
        def source = dummyBuilder().withPath("/").setAllowErrors(allowErrors).build()

        then:
        source.allowErrors() == allowErrors

        where:
        allowErrors << [false, true]
    }

    def builder(appName = "myApp") {
        ClasspathConfigSource.builder()//.setApplicationName(appName)
    }

    @Override
    AbstractConfigSource dummySource(String appName) {
        builder(appName).withPath("/").build()
    }

    @Override
    ConfigSourceBuilder dummyBuilder() {
        builder()
    }
}
