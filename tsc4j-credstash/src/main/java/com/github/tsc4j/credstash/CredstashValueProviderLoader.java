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

package com.github.tsc4j.credstash;

import com.github.tsc4j.core.AbstractTsc4jLoader;
import com.github.tsc4j.core.Tsc4jLoader;

/**
 * {@link Tsc4jLoader} implementation that is able to bootstrap {@link CredstashConfigValueProvider}
 */
public final class CredstashValueProviderLoader extends AbstractTsc4jLoader<CredstashConfigValueProvider> {
    public CredstashValueProviderLoader() {
        super(CredstashConfigValueProvider.class, CredstashConfigValueProvider::builder,
            CredstashConfigValueProvider.TYPE, "Provides values from credstash secrets store.");
    }
}
