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

import ch.qos.logback.classic.Level;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.slf4j.LoggerFactory;

import static org.slf4j.Logger.ROOT_LOGGER_NAME;

/**
 * {@link LoggerConfigurer} that is able to configure <a href="https://logback.qos.ch/">logback logger</a>.
 */
@Slf4j
public final class LogbackConfigurer implements LoggerConfigurer {
    @Override
    public boolean configure(@NonNull String level) {
        return configure(ROOT_LOGGER_NAME, level);
    }

    private boolean configure(@NonNull String loggerName, @NonNull String level) {
        try {
            val logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(loggerName);
            val oldLevel = logger.getLevel();
            val newLevel = Level.valueOf(level.trim().toUpperCase());
            if (!oldLevel.equals(newLevel)) {
                log.debug("configuring logback logger {} from level {} to {}", loggerName, oldLevel, newLevel);
                logger.setLevel(newLevel);
                return true;
            }
        } catch (Exception e) {
            // we don't really care about this, return false;
        }
        return false;
    }
}
