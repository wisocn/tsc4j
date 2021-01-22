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

package com.github.tsc4j.guice

import com.github.tsc4j.api.ReloadableConfig
import com.google.inject.AbstractModule
import com.google.inject.Injector
import com.typesafe.config.Config
import spock.guice.UseModules
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

import javax.inject.Inject

@Unroll
@UseModules([Test1Module])
@RestoreSystemProperties
class ReloadableJacksonTsc4jModule2Spec extends Specification {
    static String appName = "someApp"
    static String datacenter = "myDatacenter"
    static List<String> envs = ["test", "funky"]

    @Inject
    Injector injector

    @Inject
    Config config

    @Shared
    @Inject
    @AutoCleanup
    ReloadableConfig rc

    def "should inject all dependencies"() {
        expect:
        injector != null
        config != null
        rc != null

        rc.isReverseUpdateOrder() == true
    }

    def "should provide expected config"() {
        expect:
        config.is(rc.getSync())

        when:
        def name = config.getString('spring.application.name')

        then:
        name == "mySuperFunkyApp"
        config.getInt('app.var2') == 42
        config.getString("app.var3") == "overriden in funky/application.conf: " + name
    }

    static class Test1Module extends AbstractModule {
        @Override
        protected void configure() {
            System.setProperty("tsc4j.config", "/custom-factory-config-test-1.conf")
            install(new ReloadableConfigModule(appName, datacenter, envs))
        }
    }
}
