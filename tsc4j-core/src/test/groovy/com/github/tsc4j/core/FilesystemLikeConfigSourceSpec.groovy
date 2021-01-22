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

import com.github.tsc4j.core.AbstractConfigSourceSpec
import com.github.tsc4j.core.ConfigSource
import com.github.tsc4j.core.ConfigSourceBuilder
import com.github.tsc4j.core.FilesystemLikeConfigSource
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import groovy.util.logging.Slf4j

@Slf4j
abstract class FilesystemLikeConfigSourceSpec extends AbstractConfigSourceSpec {

    protected String getNonExistentConfigPath() {
        def num = (long) (Math.random() * 10000000000)
        "/non-existing-path-" + num + '-${env}-${application}'
    }

    /**
     * Tells whether actual configuration fetching tests should be performed.
     * @return true/false
     */
    protected boolean fetchConfigs() {
        true
    }

    def "withConfig() should set properties from a map"() {
        given:
        def builder = (FilesystemLikeConfigSource.Builder) dummyBuilder()
            .setConfdEnabled(false)
            .setWarnOnMissing(false)
            .setFailOnMissing(false)
            .setPaths([])

        def map = [
            confdEnabled        : "true",
            "warnOnMissingPaths": "true",
            "failOnMissingPaths": "true",
            "paths"             : paths
        ]
        def cfgObj = ConfigValueFactory.fromMap(map)
        def cfg = ConfigFactory.empty().withFallback(cfgObj)

        when:
        def res = builder.withConfig(cfg)

        then:
        res.is(builder)

        builder.isConfdEnabled() == true
        builder.isWarnOnMissing() == true
        builder.isFailOnMissing() == true
        builder.getPaths() == ["/foo", "/bar", "/baz"]

        where:
        paths << [
            //"/foo ; /bar, /baz ", "/foo ; /bar; /baz;/baz ",
            ["/foo", "/bar", "/baz"]]
    }

    def "configuration fetch should fail for missing paths"() {
        given:
        def envs = ["a", "b"]
        ConfigSource source = dummyBuilder()
            .setFailOnMissing(true)
            .setPaths([])
            .withPath(getNonExistentConfigPath())
            .build()

        when:
        def config = source.get()

        then:
        def thrown = thrown(RuntimeException)

        when:
        log.info("thrown: {}", thrown.toString())

        then:
        true
    }

    def "configuration fetch should return empty config for missing paths"() {
        if (!runFetchForMissingPathsTest()) {
            return
        }

        given:
        def envs = ["a", "b"]
        ConfigSource source = complexBuilder()
            .setFailOnMissing(false)
            .setPaths([])
            .withPath(getNonExistentConfigPath())
            .build()

        when:
        def config = source.get(defaultConfigQuery)

        then:
        config.isEmpty()
    }

    protected boolean runFetchForMissingPathsTest() {
        true
    }

    /**
     * Creates complex builder, possibly configured with 3rd party credentials
     * @return
     */
    ConfigSourceBuilder complexBuilder() {
        dummyBuilder()
    }
}
