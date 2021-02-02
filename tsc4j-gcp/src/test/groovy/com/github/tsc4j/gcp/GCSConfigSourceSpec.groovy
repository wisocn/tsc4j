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

package com.github.tsc4j.gcp

import com.github.tsc4j.core.AbstractConfigSource
import com.github.tsc4j.core.ConfigSourceBuilder
import com.github.tsc4j.core.FilesystemLikeConfigSourceSpec
import com.github.tsc4j.core.Tsc4jImplUtils
import com.typesafe.config.ConfigValueFactory
import groovy.util.logging.Slf4j
import spock.lang.Ignore
import spock.lang.Requires
import spock.lang.Unroll

import static com.typesafe.config.ConfigFactory.empty

@Slf4j
class GCSConfigSourceSpec extends FilesystemLikeConfigSourceSpec {
    static final String CREDS_FILE = "gcs-key.json"

    @Override
    protected String getNonExistentConfigPath() {
        return "gs://some_bucket" + super.getNonExistentConfigPath()
    }

    @Override
    protected boolean fetchConfigs() {
        Boolean.getBoolean("integration")
    }

    // test is run only if jvm is invoked with -Dintegration
    @Requires({ sys.integration })
    def "should load all available configs from GCS"() {
        given: "setup vars"
        def appName = "very-nice-application"
        def envName = "foo"
        def prefix = 'gs://tsc4j-test/${application}'

        def builder = builder(appName)
            .setCredentialsFile(CREDS_FILE)
            .withPath(prefix + '/config/${env}')
            .withPath(prefix + 'nonexistent')
            .withPath(prefix + '/config2/${env}')
            .withPath('gs://non-existent-gcs-bucket/${application}/config2/${env}')

        and:
        def source = builder.build()
        log.info("created source: {}", source)
        log.info("paths: {}", source.getPaths())

        when:
        def config = source.get(defaultConfigQuery)
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

    @Ignore
    @Unroll
    def "should read credentials from correct builder property"() {
        given:
        def source = builder().withPath("gs://x/y").build()

        when: "simulate creating credential provider"
        def inputStreamOpt = source.openCredentials(b)

        then:
        inputStreamOpt.isPresent()

        when:
        def inputStream = inputStreamOpt.get()

        then:
        inputStream != null
        inputStream.getClass() == inputStreamClass

        where:
        b                                                                     | inputStreamClass
        builder().setCredentialsFile(CREDS_FILE)                              | BufferedInputStream
        builder().setCredentialsString("blah")                                | ByteArrayInputStream
        builder().setCredentialsFile(CREDS_FILE).setCredentialsString("blah") | ByteArrayInputStream
    }

    def "withConfig() should set gcs specific map properties"() {
        given:
        def map = [
            "credentialsFile"  : UUID.randomUUID().toString(),
            "credentialsString": UUID.randomUUID().toString()
        ]
        def value = ConfigValueFactory.fromAnyRef(map)
        def config = empty().withFallback(value)

        and:
        def builder = builder()

        when:
        builder.withConfig(config)

        then:
        builder.getCredentialsFile() == map['credentialsFile']
        builder.getCredentialsString() == map['credentialsString']
    }

    def "should create GCS config source instance: #impl"() {
        when:
        def config = ConfigValueFactory.fromMap([impl: impl, paths: ["gs://bucket/"]]).toConfig()
        def instance = Tsc4jImplUtils.createConfigSource(config, 1).get()

        then:
        instance instanceof GCSConfigSource

        where:
        impl << [
            "gcp.gcs",
            "GCP.gcs",
            " GCP.gcS ",
            "gcs",
            "GCS",
            "GCs",
            "  gCs ",
            "GCSConfigSource",
            "com.github.tsc4j.gcp.GCSConfigSource"
        ]
    }

    GCSConfigSource.Builder builder(appName = "appName") {
        GCSConfigSource.builder()
    }

    @Override
    AbstractConfigSource dummySource(String appName) {
        builder(appName).withPath("gs://bucket/name").build()
    }

    @Override
    ConfigSourceBuilder dummyBuilder() {
        builder()
    }

    @Override
    ConfigSourceBuilder complexBuilder() {
        dummyBuilder().setCredentialsFile(CREDS_FILE)
    }

    @Override
    protected boolean runFetchForMissingPathsTest() {
        false
    }
}
