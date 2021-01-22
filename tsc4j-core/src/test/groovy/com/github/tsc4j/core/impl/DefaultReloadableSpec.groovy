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

import com.github.tsc4j.api.Reloadable
import groovy.util.logging.Slf4j
import spock.lang.Unroll

import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer
import java.util.function.Function

@Unroll
@Slf4j
class DefaultReloadableSpec extends AbstractReloadableSpec {
    static final String PATH = "reloadable"
    static final AtomicLong idCount = new AtomicLong()
    static final AtomicLong consumerCount = new AtomicLong()
    static Consumer<DefaultReloadable<?>> closeConsumer = { consumerCount.incrementAndGet() }

    @Override
    protected <T> Reloadable<T> emptyReloadable() {
        new DefaultReloadable<T>(idCount.incrementAndGet(), PATH, Function.identity(), closeConsumer)
    }

    @Override
    protected <T> Reloadable<T> createReloadable(T value) {
        new DefaultReloadable<T>(idCount.incrementAndGet(), PATH, Function.identity(), closeConsumer).setValue(value)
    }

    @Override
    protected <T> Reloadable<T> updateReloadable(Reloadable<T> reloadable, T value) {
        reloadable.setValue(value)
    }

    @Override
    protected <T> Reloadable<T> removeReloadableValue(Reloadable<T> reloadable) {
        ((DefaultReloadable<T>) reloadable).removeValue()
        reloadable
    }
}
