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

package com.github.tsc4j.core.impl;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * {@link java.util.ServiceLoader} utilities.
 *
 * @see ServiceLoader
 * @see <a href="https://docs.oracle.com/javase/tutorial/ext/basics/spi.html">Creating Extensible Applications</a>
 */
@Slf4j
@UtilityClass
public class SvcLoader {
    private static final Comparator<?> COMPARATOR = (a, b) -> {
        if (a == null && b == null) {
            return 0;
        } else if (a == null) {
            return -1;
        } else if (b == null) {
            return 1;
        }

        if (a instanceof Comparable && b instanceof Comparable) {
            return ((Comparable) a).compareTo((Comparable) b);
        }

        return a.getClass().getName().compareTo(b.getClass().getName());
    };

    /**
     * Returns first given {@link ServiceLoader} interface class registered implementation that can be initialized on
     * running JVM, catching all exceptions that might occur during their initialization.
     *
     * @param <T>   service loader interface class type
     * @param clazz service loader interface class
     * @return optional of first successfully initialized implementation.
     * @throws IllegalArgumentException if given argument is not an interface.
     */
    public <T> Optional<T> first(@NonNull Class<T> clazz) {
        return orderedStream(clazz).findFirst();
    }

    /**
     * Returns <b>unordered</b> stream of all {@link ServiceLoader} interface class registered implementations that can
     * be initialized on running JVM, catching all exceptions that might occur during their initialization.
     *
     * @param serviceClass service interface class
     * @param <T>          service interface class type
     * @return stream of successfully initialized implementations, excluding implementation that did not successfully
     * instantiate.
     * @throws IllegalArgumentException if given argument is not an interface.
     */
    public <T> Stream<T> unorderedStream(@NonNull Class<T> serviceClass) {
        if (!serviceClass.isInterface()) {
            throw new IllegalArgumentException(
                "Can't find instances on classpath for non-interface class: " + serviceClass.getName());
        }

        try {
            val iterator = new LazyIterator<>(ServiceLoader.load(serviceClass).iterator(), serviceClass);
            return StreamSupport
                .stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false)
                .filter(Objects::nonNull);
        } catch (Throwable t) {
            log.debug(
                "exception while instantiating service-loader stream of implementations of: {}",
                serviceClass.getName(), t);
            return Stream.empty();
        }
    }

    /**
     * Returns ordered result of {@link #unorderedStream(Class)}.
     *
     * @param serviceClass service interface class
     * @param <T>          service interface class type
     * @return stream of successfully initialized implementations, excluding implementation that did not successfully
     * instantiate.
     * @throws IllegalArgumentException if given argument is not an interface.
     * @see #unorderedStream(Class)
     */
    public <T> Stream<T> orderedStream(@NonNull Class<T> serviceClass) {
        return orderedStream(serviceClass).sorted((Comparator<? super T>) COMPARATOR);
    }


    /**
     * Returns all given {@link ServiceLoader} interface class registered implementations that can be initialized on
     * running JVM, catching all exceptions that might occur during their initialization.
     * <p></p>
     * <b>NOTE:</b> list elements are being alphabetically sorted by their class name.
     *
     * @param serviceClass service interface class
     * @param <T>          service interface class type
     * @return ordered list of successfully initialized implementations.
     * @throws IllegalArgumentException if given argument is not an interface.
     * @see #unorderedStream(Class)
     */
    public <T> List<T> load(@NonNull Class<T> serviceClass) {
        return orderedStream(serviceClass).collect(Collectors.toList());
    }

    @RequiredArgsConstructor
    private static class LazyIterator<T> implements Iterator<T> {
        @NonNull
        private final Iterator<T> delegate;
        @NonNull
        private final Class<T> service;

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public T next() {
            try {
                return delegate.next();
            } catch (Throwable t) {
                // YEP, ServiceLoader's Iterator throws `Error`, therefore we need to catch Throwable
                log.debug("error loading next service loader instance for: {}", service.getName(), t);
                return null;
            }
        }
    }
}
