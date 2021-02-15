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

import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tsc4j.core.AbstractConfigSource
import com.github.tsc4j.core.AbstractConfigSourceSpec
import com.github.tsc4j.core.ConfigQuery
import com.github.tsc4j.core.ConfigSourceBuilder
import com.github.tsc4j.core.Tsc4j
import com.typesafe.config.ConfigFactory
import de.mkammerer.wiremock.WireMockExtension
import groovy.util.logging.Slf4j
import org.junit.jupiter.api.extension.RegisterExtension
import spock.lang.Unroll

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse
import static com.github.tomakehurst.wiremock.client.WireMock.get
import static com.github.tomakehurst.wiremock.client.WireMock.givenThat
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo

@Unroll
@Slf4j
class URLConfigSourceSpec extends AbstractConfigSourceSpec {
    static int PORT = 12891
    static def cfgA = ConfigFactory.parseMap([
        "foo": "bar"])
    static def cfgB = ConfigFactory.parseMap([
        "foo": "baz",
        "x"  : "y"])

    static def wiremockOpts = new WireMockConfiguration().port(PORT)

    @RegisterExtension
    WireMockExtension wireMock = new WireMockExtension(PORT);

    def setup() {
        wireMock.start()
    }

    def cleanup() {
        wireMock.stop()
    }

//    @Rule
//    WireMockRule wiremockRule = new WireMockRule(wiremockOpts, false)

    def "should fetch correct urls and produce expected config"() {
        given:
        def source = dummySource()
        def query = ConfigQuery.builder()
                               .appName("myApp")
                               .datacenter("someDc")
                               .envs(["envA", "envB"])
                               .build()

        and: "setup wiremock"
        mockHttpResponses().each { givenThat(it) }

        when: "fetch config"
        def config = source.get(query)
        log.info("fetched config: {}", Tsc4j.render(config, true))

        then:
        config.root().size() == 2
        config.getString("foo") == "baz"
        config.getString("x") == "y"
    }

    def mockHttpResponses() {
        [
            get(urlEqualTo('/someDc/envA/myApp/foo.conf'))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/plain")
                    .withFixedDelay(10)
                    .withBody(Tsc4j.render(cfgA))),

            get(urlEqualTo('/someDc/envB/myApp/bar.conf'))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withFixedDelay(500)
                    .withBody(Tsc4j.render(cfgB)))
        ]
    }

    // TODO: implement some actual tests.

    @Override
    AbstractConfigSource dummySource(String appName) {
        dummyBuilder()
            .url("http://localhost:$PORT/" + '${datacenter}/${env}/${application}/foo.conf')
            .url("http://localhost:$PORT/" + '${datacenter}/${env}/${application}/bar.conf')
            .url("http://localhost:$PORT/non-existing.conf")
            .setFailOnMissing(false)
            .build()
    }

    @Override
    ConfigSourceBuilder dummyBuilder() {
        URLConfigSource.builder()
    }
}
