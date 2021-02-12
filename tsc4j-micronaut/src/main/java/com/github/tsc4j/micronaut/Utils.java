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

package com.github.tsc4j.micronaut;

import com.github.tsc4j.core.AtomicInstance;
import com.github.tsc4j.core.CloseableReloadableConfig;
import com.github.tsc4j.core.Pair;
import com.github.tsc4j.core.Tsc4jImplUtils;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Utils {
    private static final AtomicInstance<Pair<CloseableReloadableConfig, Tsc4jPropertySource>> instanceHolder =
        new AtomicInstance<>(Utils::destroyInstanceHolder);

    /**
     * Returns instance holder.
     *
     * @return instance holder
     */
    AtomicInstance<Pair<CloseableReloadableConfig, Tsc4jPropertySource>> instanceHolder() {
        return instanceHolder;
    }

    private static void destroyInstanceHolder(Pair<CloseableReloadableConfig, Tsc4jPropertySource> pair) {
        Tsc4jImplUtils.close(pair.second());
        Tsc4jImplUtils.close(pair.first());
    }
}
