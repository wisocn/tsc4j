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

import com.typesafe.config.Config;
import lombok.Getter;

/**
 * Base class for writing {@link AbstractConfigSource} builder aliases.
 */
public abstract class ConfigSourceBuilder<T extends ConfigSourceBuilder<T>> extends AbstractBuilder<ConfigSource, T> {
    /**
     * Tells whether warning should be logged when config source will try to read non-existing config location.
     */
    @Getter
    private boolean warnOnMissing = true;

    /**
     * Tells whether fetch error is issued when any of configuration locations do not exist.
     */
    @Getter
    private boolean failOnMissing = false;

    /**
     * Sets a flag indicating whether warning should be logged when config source will try to open/list non-existing
     * paths.
     *
     * @param warnOnMissing true/false
     * @return reference to itself
     */
    public T setWarnOnMissing(boolean warnOnMissing) {
        this.warnOnMissing = warnOnMissing;
        return getThis();
    }

    /**
     * Sets a flag indicating whether fetch should fail when config source will try to open/list non-existing
     * paths.
     *
     * @param failOnMissing true/false
     * @return reference to itself
     */
    public T setFailOnMissing(boolean failOnMissing) {
        this.failOnMissing = failOnMissing;
        return getThis();
    }

    @Override
    public void withConfig(Config config) {
        super.withConfig(config);

        cfgBoolean(config, "warn-on-missing", this::setWarnOnMissing);
        cfgBoolean(config, "fail-on-missing", this::setFailOnMissing);
    }
}
