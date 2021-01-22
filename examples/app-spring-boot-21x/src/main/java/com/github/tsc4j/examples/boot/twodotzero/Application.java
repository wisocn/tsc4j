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

package com.github.tsc4j.examples.boot.twodotzero;

import com.github.tsc4j.api.Reloadable;
import com.github.tsc4j.api.ReloadableConfig;
import com.typesafe.config.ConfigRenderOptions;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;

@Slf4j
@RefreshScope
@RestController
@SpringBootApplication
public class Application {
    @Autowired
    ReloadableConfig reloadableConfig;

    @Autowired
    ReloadableConfig rc;

    @Value("${my.config.foo}")
    String fooString;

    @Autowired
    Reloadable<AppConfig> appConfigReloadable;

    public static void main(String... args) {
        SpringApplication.run(Application.class, args);
    }

    @PostConstruct
    void init() {
        log.info("app initialized, we have the following config: {}", appConfigReloadable.get());
        log.info("foo string: {}", fooString);
    }

    @GetMapping("/")
    public AppConfig getConfig() {
        return appConfigReloadable.get();
    }

    @GetMapping("/str")
    public String getFoo() {
        return fooString;
    }

    @GetMapping("/config")
    public String fullConfig(@RequestParam(defaultValue = "false") boolean verbose) {
        val config = reloadableConfig.getSync();
        val renderOpts = verbose ? ConfigRenderOptions.defaults() : ConfigRenderOptions.concise().setFormatted(true);
        return config.root().render(renderOpts);
    }
}
