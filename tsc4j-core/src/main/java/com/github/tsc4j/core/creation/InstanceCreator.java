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

package com.github.tsc4j.core.creation;

import com.github.tsc4j.api.Ordered;
import com.github.tsc4j.api.WithConfig;
import lombok.NonNull;

import java.util.Collections;
import java.util.Set;

/**
 * Creates instance of a specified type.
 *
 * @param <B> type being created by this creator
 */
public interface InstanceCreator<B> extends Ordered, Comparable<InstanceCreator<B>>, WithConfig {
    /**
     * Implementation type name, should return a lower-cased type name.
     *
     * @return implementation type name
     * @see #typeAliases()
     * @see #supports(String)
     */
    String type();

    /**
     * Implementation alias names; actual implementations should return set of lower-cased names.
     *
     * @return set of lower-cased implementation alias names.
     * @see #type()
     * @see #supports(String)
     */
    default Set<String> typeAliases() {
        return Collections.emptySet();
    }

    /**
     * Returns description for this creator.
     *
     * @return description
     */
    String description();

    /**
     * Tells whether this instance creator supports creation of type {@code B} with given implementation type name.
     *
     * @param implType implementation type name
     * @return true/false if this creator supports creating given implementation name
     */
    boolean supports(@NonNull String implType);

    /**
     * Returns class that this creator  is able to create.
     *
     * @return class that this creator creates.
     */
    Class<? extends B> creates();

    /**
     * Creates new type.
     *
     * @return returns created type
     * @throws RuntimeException when instance can't be created.
     */
    B build();
}
