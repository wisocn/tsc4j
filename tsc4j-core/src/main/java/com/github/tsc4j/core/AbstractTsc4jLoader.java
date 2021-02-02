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

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.val;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Base class for {@link Tsc4jLoader} aliases.
 */
@EqualsAndHashCode
public abstract class AbstractTsc4jLoader<T> implements Tsc4jLoader<T> {
    private static final Comparator<Tsc4jLoader> COMPARATOR =
        Comparator.comparing((Tsc4jLoader e) -> e.forClass().getName())
            .thenComparing(Tsc4jLoader::name)
            .thenComparing(Tsc4jLoader::getPriority);

    private final String name;
    private final Set<String> aliases;

    private final Class<T> forClass;
    private final Supplier<AbstractBuilder> builderSupplier;
    private final String description;

    /**
     * Creates new instance.
     *
     * @param forClass        for class instance, see {@link #forClass()}
     * @param builderSupplier builder supplier, see {@link #getBuilder()}
     * @param name            primary implementation name
     * @param description     implementation description, see {@link #description()}
     * @param aliases         implementation names, see {@link #aliases()}
     */
    protected AbstractTsc4jLoader(@NonNull Class<T> forClass,
                                  @NonNull Supplier<AbstractBuilder> builderSupplier,
                                  @NonNull String name,
                                  @NonNull String description,
                                  @NonNull String... aliases) {
        this(forClass, builderSupplier, name, description, Arrays.asList(aliases));
    }

    /**
     * Creates new instance.
     *
     * @param forClass        for class instance, see {@link #forClass()}
     * @param builderSupplier builder supplier, see {@link #getBuilder()}
     * @param name            primary implementation name
     * @param description     implementation description, see {@link #description()}
     * @param implementations implementation names, see {@link #aliases()}
     */
    protected AbstractTsc4jLoader(@NonNull Class<T> forClass,
                                  @NonNull Supplier<AbstractBuilder> builderSupplier,
                                  @NonNull String name,
                                  @NonNull String description,
                                  @NonNull Collection<String> implementations) {
        this.forClass = forClass;
        this.builderSupplier = builderSupplier;
        this.name = name;
        this.description = description;
        val impls = Tsc4jImplUtils.uniqStream(implementations)
            .map(String::toLowerCase)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        this.aliases = Collections.unmodifiableSet(impls);
    }

    @Override
    public final String name() {
        return name;
    }

    @Override
    public final Set<String> aliases() {
        return aliases;
    }

    @Override
    public final Class<T> forClass() {
        return forClass;
    }

    @Override
    @SuppressWarnings("unchecked")
    public final Optional<AbstractBuilder<T, ?>> getBuilder() {
        return Optional.ofNullable(builderSupplier.get());
    }

    @Override
    public final String description() {
        return description;
    }

    @Override
    public Optional<T> getInstance() {
        return Optional.empty();
    }

    @Override
    public int compareTo(Tsc4jLoader other) {
        return COMPARATOR.compare(this, other);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(name=" + name() + ", aliases=" + aliases() +
            ", forClass=" + forClass.getName() + ")";
    }
}
