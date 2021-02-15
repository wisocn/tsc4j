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

import com.github.tsc4j.core.Tsc4j
import com.github.tsc4j.core.Tsc4jConfig
import com.typesafe.config.ConfigFactory
import groovy.util.logging.Slf4j
import spock.lang.Specification

import java.time.Duration

@Slf4j
class Tsc4jConfigSpec extends Specification {
    static final def config = ConfigFactory.parseResources("tsc4j-with-transformer.conf")

    def "empty instance should contain expected defaults"() {
        when:
        def cfg = Tsc4jConfig.builder().build()

        then:
        cfg.getRefreshInterval().toMinutes() == 2
        cfg.getRefreshIntervalJitterPct() == 25
        cfg.isReverseUpdateOrder() == false
        cfg.isCliEnabled() == true

        cfg.getSources().isEmpty()
        cfg.getTransformers().isEmpty()
        cfg.getValueProviders().isEmpty()
    }

    def "withConfig() on builder should correctly configure builder instance"() {
        given:
        def builder = Tsc4jConfig.builder()

        when:
        builder.withConfig(config)
        def cfg = builder.build()
        log.warn("created instance: {}", cfg)

        then:
        assertInstance(cfg)
    }

    def "should properly deserialize config object"() {
        when:
        def cfg = Tsc4j.toBean(config, Tsc4jConfig)
        log.info("deserialized config: {}", cfg)

        then:
        cfg.getRefreshInterval() == Duration.ofSeconds(6)
        cfg.getRefreshIntervalJitterPct() == 27
        !cfg.isReverseUpdateOrder()

        !cfg.getSources().isEmpty()
        !cfg.getTransformers().isEmpty()

        when:
        def sources = cfg.getSources()
        log.info("sources: {}", sources)

        then:
        sources.size() == 3

        sources[0].root().size() == 1
        sources[0].getBoolean("enabled") == true

        sources[1].root().size() == 1
        sources[1].getString("impl") == "classpath"

        sources[2].root().size() == 2
        sources[2].getBoolean("enabled") == false
        sources[2].getString("impl") == "Files"

        when:
        def transformers = cfg.getTransformers()
        log.info("transformers: {}", transformers)

        then:
        transformers.size() == 3

        transformers[0].root().size() == 1
        transformers[0].getBoolean("enabled") == true

        transformers[1].root().size() == 2
        transformers[1].getBoolean("enabled") == false
        transformers[1].getString("impl") == "Rot13"

        transformers[2].root().size() == 3
        transformers[2].getString("impl") == "rot-13"
        transformers[2].getString("name") == "decryptor"
    }

    def assertInstance(Tsc4jConfig cfg) {
        assert cfg != null

        cfg.getRefreshInterval() == Duration.ofSeconds(6)
        cfg.getRefreshIntervalJitterPct() == 27
        !cfg.isReverseUpdateOrder()

        !cfg.getSources().isEmpty()
        !cfg.getTransformers().isEmpty()

        // inspect sources
        def sources = cfg.getSources()
        log.info("sources: {}", sources)

        assert sources.size() == 3

        assert sources[0].root().size() == 1
        assert sources[0].getBoolean("enabled") == true

        assert sources[1].root().size() == 1
        assert sources[1].getString("impl") == "classpath"

        assert sources[2].root().size() == 2
        assert sources[2].getBoolean("enabled") == false
        assert sources[2].getString("impl") == "Files"

        // transformers
        def transformers = cfg.getTransformers()
        log.info("transformers: {}", transformers)

        assert transformers.size() == 3

        assert transformers[0].root().size() == 1
        assert transformers[0].getBoolean("enabled") == true

        assert transformers[1].root().size() == 2
        assert transformers[1].getString("impl") == "Rot13"

        assert transformers[2].root().size() == 3
        assert transformers[2].getString("impl") == "rot-13"
        assert transformers[2].getString("name") == "decryptor"

        // value providers
        def valProviders = cfg.getValueProviders()
        log.info("value providers: {}", valProviders)

        assert valProviders.size() == 4

        assert valProviders[0].isEmpty()

        assert valProviders[1].root().size() == 2
        assert valProviders[1].getBoolean("optional") == true
        assert valProviders[1].getString("impl") == "foo"

        assert valProviders[2].root().size() == 2
        assert valProviders[2].getString("impl") == "bar"
        assert valProviders[2].getBoolean("enabled") == false

        assert valProviders[3].root().size() == 1
        assert valProviders[3].getString("impl") == "noop"

        true
    }
}
