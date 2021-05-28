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

package com.github.tsc4j.spring

import com.github.tsc4j.api.Reloadable
import com.github.tsc4j.api.ReloadableConfig
import com.github.tsc4j.api.Tsc4jConfigPath
import com.typesafe.config.Config
import groovy.transform.ToString
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.stereotype.Service

import javax.annotation.PostConstruct

@Slf4j
@SpringBootApplication
@EnableTsc4j
class SampleApp {
    @Autowired
    ReloadableConfig reloadableConfig

    @Autowired
    Config config

    @Autowired
    MyService myService

    void main(String... args) {
        SpringApplication.run(SampleApp, args)
    }

    @PostConstruct
    void init() {
        log.info("{} init()", this)
        log.info("Using reloadable config: {}", reloadableConfig)
        log.info("Using config:            {}", config)
        log.info("configs are the same:    {}", config == reloadableConfig.getSync())
        log.info("")
        log.info("config:         {}", config)
        log.info("my service:     {}", myService)
        //log.info("my config:      {}", myService.myconfigReloadable.get())

        log.info("{} init() done", this)
    }

    @Slf4j
    @Service
    @ToString(includePackage = false, includeNames = true)
    class MyService {
        boolean myBoolean = true
        String appName
        Reloadable<MyConfig> myconfigReloadable

        @Autowired
        MyService(ReloadableConfig rc,
                  @Value('${spring.application.name}') String appName) {
            this(rc.register(MyConfig), appName)
        }

        MyService(Reloadable<MyConfig> myConfigReloadable,
                  @Value('${spring.application.name}') String appName) {
            this.myconfigReloadable = myConfigReloadable
            this.appName = appName
            log.info("{} instantiating.", this)
        }

        def setBool(Boolean b) {
            if (b == null) {
                log.warn("setBool disappeared")
            } else {
                this.myBoolean = b.booleanValue()
                log.warn("setBool: set myboolean to: {}", myBoolean)
            }

            def x = new MyConfig()
        }
    }

    @Tsc4jConfigPath("app.myConfig")
    @ToString(includePackage = false, includeNames = true)
    static class MyConfig {
        boolean flag = false
        String str = "defaultStr"
    }
}
