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

package com.github.tsc4j.api;

import com.typesafe.config.Config;
import lombok.NonNull;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Interface that provides ability to always obtain most recent {@link Config} instance and register for partial
 * configuration updates.
 *
 * @see Config
 */
public interface ReloadableConfig {
    /**
     * Configuration path that represent root {@link Config} instance (value: <b>{@value}</b>)
     */
    String ROOT_PATH = "";

    /**
     * Tells whether configuration value is already present, so that {@link #getSync()} will not block and return
     * immediately.
     *
     * @return true/false
     * @see #get()
     * @see #getSync()
     */
    boolean isPresent();

    /**
     * Returns {@link CompletionStage} that will get completed with most recent loaded configuration when it's fetched;
     * completed {@link CompletionStage} is returned if configuration is already fetched.
     *
     * @return completion stage that will be eventually completed with most recent {@link Config} instance.
     */
    CompletionStage<Config> get();

    /**
     * Retrieves most recent loaded configuration by optionally blocking calling thread if first configuration fetch has
     * not been completed already.
     *
     * @return config object
     * @throws RuntimeException if configuration cannot be retrieved.
     * @see #get()
     */
    Config getSync() throws RuntimeException;

    /**
     * Registers new {@link Reloadable} that uses custom converter that maps root configuration object to a value.
     *
     * @param converter converter function that maps root {@link Config} object to desired value.
     * @param <T>       value type
     * @return reloadable
     * @throws NullPointerException in case of null arguments
     */
    <T> Reloadable<T> register(@NonNull Function<Config, T> converter);

    /**
     * Registers new {@link Reloadable} that gets mapped to a custom bean or primitive.
     *
     * @param path configuration path
     * @param <T>  value type
     * @return reloadable
     * @throws NullPointerException in case of null arguments
     * @see Config#getConfig(String)
     * @see Config#getValue(String)
     */
    <T> Reloadable<T> register(@NonNull String path, @NonNull Class<T> clazz);

    /**
     * Registers new {@link Reloadable} that gets mapped to specified bean class. Bean class needs to be annotated with
     * {@link Tsc4jConfigPath} for this method to succeed.
     *
     * @param clazz bean class
     * @param <T>   bean type
     * @return reloadable
     * @throws NullPointerException     in case of null arguments
     * @throws IllegalArgumentException when bad bean class has been passed
     * @see Tsc4jConfigPath
     */
    <T> Reloadable<T> register(@NonNull Class<T> clazz);
}
