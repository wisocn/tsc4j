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

import com.typesafe.config.Config;
import lombok.Getter;
import lombok.NonNull;
import lombok.val;

import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.Objects;

/**
 * Base class for writing {@link InstanceCreator} or various instance builder implementations that need to be
 * discoverable via {@link java.util.ServiceLoader}.
 *
 * @param <T> instance creator type
 */
public abstract class AbstractInstanceCreator<B, T extends AbstractInstanceCreator<B, T>>
    implements InstanceCreator<B> {
    /**
     * Comparator for sorting multiple instances of the same type,
     */
    private static final Comparator<InstanceCreator<?>> COMPARATOR =
        Comparator.comparing((InstanceCreator<?> it) -> it.getOrder())
            .thenComparing(it -> it.type())
            .thenComparing(it -> it.getClass().getName());

    /**
     * Built instance name.
     */
    @Getter
    private String name = null;

    /**
     * Flag that tells whether errors should be tolerated (default: {@code false})
     */
    @Getter
    private boolean allowErrors = false;

    /**
     * Flag that tells whether final instance will attempt to perform operations concurrently if possible (default:
     * {@code true})
     */
    @Getter
    private boolean parallel = true;

    /**
     * Clock used for time operations.
     */
    @Getter
    private Clock clock = Clock.systemDefaultZone();

    /**
     * Cache TTL for cache operations.
     */
    @Getter
    private Duration cacheTtl = defaultCacheTtl();

    /**
     * Defines default cache TTL.
     *
     * @return cache ttl duration.
     * @see #getCacheTtl()
     * @see #setCacheTtl(Duration)
     */
    protected Duration defaultCacheTtl() {
        return Duration.ZERO;
    }

    /**
     * Sets built instance name.
     *
     * @param name name
     * @return reference to itself
     * @see #getName()
     */
    public T setName(String name) {
        this.name = name;
        return getThis();
    }

    /**
     * Sets whether exceptions from built instance should be tolerated.
     *
     * @param flag true/false
     * @return reference to itself.
     * @see #isAllowErrors()
     */
    public T setAllowErrors(boolean flag) {
        this.allowErrors = flag;
        return getThis();
    }

    /**
     * Sets the flag indicating whether final instance will attempt to perform operations concurrently (if possible).
     *
     * @param parallel flag
     * @return reference to itself
     * @see #isParallel()
     */
    public T setParallel(boolean parallel) {
        this.parallel = parallel;
        return getThis();
    }

    /**
     * Sets the clock for time operations.
     *
     * @param clock clock
     * @see #getClock()
     */
    public T setClock(@NonNull Clock clock) {
        this.clock = clock;
        return getThis();
    }

    /**
     * Sets cache ttl for cache operations.
     *
     * @param cacheTtl cache ttl
     * @return reference to itself
     * @see #getCacheTtl()
     */
    public T setCacheTtl(@NonNull Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
        return getThis();
    }

    @Override
    public boolean supports(@NonNull String implType) {
        val implName = implType.trim();
        if (implName.isEmpty()) {
            return false;
        }

        return implName.equalsIgnoreCase(type()) ||
            typeAliases().stream().filter(Objects::nonNull).anyMatch(it -> implName.equalsIgnoreCase(it)) ||
            creates().getSimpleName().equals(implName) ||
            creates().getName().equals(implName);
    }

    @Override
    public void withConfig(@NonNull Config config) {
        cfgString(config, "name", this::setName);
        cfgBoolean(config, "allow-errors", this::setAllowErrors);
        cfgBoolean(config, "parallel", this::setParallel);
        cfgDuration(config, "cache-ttl", this::setCacheTtl);
    }

    @Override
    public int compareTo(InstanceCreator<B> o) {
        if (o == null) {
            return 1;
        }
        return COMPARATOR.compare(this, o);
    }

    /**
     * Returns reference to itself.
     *
     * @return reference to itself
     */
    @SuppressWarnings("unchecked")
    protected final T getThis() {
        return (T) this;
    }

    /**
     * Checks instance creator's internal state, meant to be invoked before or part of {@link #build()}. Concrete
     * implementations should always call super implementation.
     *
     * @return the reference to itself
     * @throws IllegalStateException if creator's interanal state is invalid.
     */
    public T checkState() {
        return getThis();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "@" + hashCode();
    }
}
