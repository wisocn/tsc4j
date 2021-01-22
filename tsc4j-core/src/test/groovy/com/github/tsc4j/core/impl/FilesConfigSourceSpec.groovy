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
import com.github.tsc4j.core.FilesystemLikeConfigSourceSpec
import groovy.util.logging.Slf4j
import spock.lang.Shared

@Slf4j
class FilesConfigSourceSpec extends FilesystemLikeConfigSourceSpec {
    @Shared
    def classpathConfigDir = "/bundled_config"

    def createTmpDirAndCopyConfigs() {
        copyFromClasspathToTmpDirRecursive(classpathConfigDir)
    }

    def "build should throw in case of no config names"() {
        when:
        def res = builder.build()

        then:
        thrown(RuntimeException)
        res == null

        where:
        builder << [
            builder(),
            builder().withPath(" "),
            builder().withPath(""),
            builder().withPath("", null),
        ]
    }

    def "should list all available configs from filesystem or classpath"() {
        given: "setup vars"
        def prefix = createTmpDirAndCopyConfigs()
        def envName = "foo"
        def builder = builder()
            .withPath(prefix + '/config///${env}')
            .withPath(prefix + 'nonexistent')
            .withPath(prefix + '/' + defaultAppName + '/${env}');

        and:
        FilesConfigSource supplier = builder.build()
        def query = defaultConfigQuery.toBuilder()
                                      .env(envName)
                                      .build()

        when:
        def config = supplier.get(query)
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

    FilesConfigSource.Builder builder() {
        FilesConfigSource.builder()
    }

    @Override
    AbstractConfigSource dummySource(String appName) {
        return builder().withPath(appName).build()
    }

    @Override
    ConfigSourceBuilder dummyBuilder() {
        return builder()
    }
}
