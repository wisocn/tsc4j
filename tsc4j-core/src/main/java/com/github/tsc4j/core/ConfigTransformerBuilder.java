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

/**
 * Builder for {@link AbstractConfigTransformer} based {@link ConfigTransformer} aliases.
 */
public abstract class ConfigTransformerBuilder<T extends ConfigTransformerBuilder<T>>
    extends AbstractBuilder<ConfigTransformer, T> {
    /**
     * Creates config transformer from the contents of this builder.
     *
     * @return config transformer
     * @throws IllegalStateException if builder contains invalid state
     */
    @Override
    public abstract ConfigTransformer build();
}
