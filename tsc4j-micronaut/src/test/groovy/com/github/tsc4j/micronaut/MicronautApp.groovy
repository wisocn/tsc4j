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

package com.github.tsc4j.micronaut

import groovy.util.logging.Slf4j
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.runtime.Micronaut
import io.micronaut.runtime.context.scope.Refreshable
import io.micronaut.scheduling.annotation.Scheduled

import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicInteger

@Slf4j
@Singleton
@Refreshable
class MicronautApp {
    private final def counter = new AtomicInteger()

    @Inject
    MyMicronautBean bean

    @Inject
    ApplicationContext ctx

//    @PostConstruct
//    void init() {
//        def sb = new StringBuilder("\nproperty source LOADERS:\n")
//        ctx.getEnvironment().getPropertySourceLoaders().each { sb.append(" $it (order: ${it.getOrder()})\n") }
//        sb.append("\nproperty SOURCES:\n")
//        ctx.getEnvironment().getPropertySources().each { sb.append("  $it (order: ${it.getOrder()})\n") }
//        log.info(sb.toString())
//    }

    @Scheduled(fixedRate = "1s")
    String task() {
        log.info("task: {} -> {}", counter.incrementAndGet(), bean)
        return "foo"
    }

    static void main(String... args) {
        System.setProperty(Environment.ENVIRONMENTS_PROPERTY, "dev,qa")
        log.info("starting app")
        Micronaut.run(args)
    }
}
