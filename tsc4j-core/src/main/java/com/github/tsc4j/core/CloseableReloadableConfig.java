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

package com.github.tsc4j.core;

import com.github.tsc4j.api.ReloadableConfig;
import com.typesafe.config.Config;

import java.io.Closeable;
import java.util.concurrent.CompletionStage;

/**
 * {@link ReloadableConfig} that can be refreshed and closed.
 */
public interface CloseableReloadableConfig extends ReloadableConfig, Closeable {
    /**
     * Triggers manual configuration refresh.
     *
     * @return completion stage that is going to be completed with newly fetched {@link Config} instance.
     */
    CompletionStage<Config> refresh();

    /**
     * Closes the instance, stops polling for configuration updates and releases any created resources.
     */
    @Override
    void close();
}
