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

package com.github.tsc4j.cli;

/**
 * Logging subsystem configurer.
 */
public interface LoggerConfigurer {
    /**
     * No-op logger configurer.
     */
    LoggerConfigurer NOOP = level -> false;

    /**
     * Configures root logger level.
     *
     * @param level root logger level
     * @return true if logger level was successfully changed, otherwise false
     */
    boolean configure(String level);
}
