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
import lombok.NonNull;

/**
 * Builder for {@link AbstractConfigValueProvider} based {@link ConfigValueProvider} implementations.
 */
public abstract class ValueProviderBuilder<T extends ValueProviderBuilder<T>> extends AbstractBuilder<ConfigValueProvider, T> {

    /**
     * Flag that tells whether errors should be tolerated (default: {@code false})
     */
    @Getter
    private boolean allowMissing = false;

    /**
     * Sets whether missing values should be tolerated.
     *
     * @param allowMissing flag
     * @return reference to itself
     */
    public T setAllowMissing(boolean allowMissing) {
        this.allowMissing = allowMissing;
        return getThis();
    }

    @Override
    public T withConfig(@NonNull Config config) {
        configVal(config, "allow-missing", Config::getBoolean).ifPresent(this::setAllowMissing);
        return super.withConfig(config);
    }

    /**
     * Creates config transformer from the contents of this builder.
     *
     * @return config transformer
     * @throws IllegalStateException if builder contains invalid state
     */
    @Override
    public abstract ConfigValueProvider build();
}
