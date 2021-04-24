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
import com.typesafe.config.ConfigFactory;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.val;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Base-class for writing tree-like (filesystem) configuration source aliases.
 *
 * @param <T> fetch context type
 * @see #createFetchContext(ConfigQuery)
 */
public abstract class FilesystemLikeConfigSource<T> extends AbstractConfigSource {
    /**
     * Tells whether {@code conf.d}-style loading is enabled.
     */
    @Getter(AccessLevel.PROTECTED)
    private final boolean confdEnabled;

    /**
     * Tells whether config source will verbosely log paths on first fetch.
     *
     * @see #onFirstFetch()
     * @see #getPaths()
     */
    @Getter(AccessLevel.PROTECTED)
    private final boolean verbosePaths;

    /**
     * List of configuration paths where configurations will be looked for. These config names can contain variables
     * like
     * <b>{@code ${application}}</b>, <b>{@code ${datacenter}}</b> or <b>{@code ${env}}</b> which get substituted
     * from values found in {@link ConfigQuery} instance.
     *
     * @see #get(ConfigQuery)
     * @see ConfigQuery
     * @see Builder#withPath(String...)
     * @see Builder#withPaths(Collection)
     */
    @Getter(AccessLevel.PUBLIC)
    private final List<String> paths;

    /**
     * Creates new instance.
     *
     * @param builder builder
     * @throws NullPointerException  in case of null arguments
     * @throws IllegalStateException in case of bad builder state
     */
    @SuppressWarnings("unchecked")
    protected FilesystemLikeConfigSource(@NonNull Builder builder) {
        super(builder);
        this.confdEnabled = builder.isConfdEnabled();
        this.paths = createConfigPaths(builder.getPaths());
        this.verbosePaths = builder.isVerbosePaths();
    }

    private List<String> createConfigPaths(@NonNull Collection<String> names) {
        Stream<String> stream = Tsc4jImplUtils.uniqStream(names)
            .filter(this::sanitizePathFilter);

        if (removeDoubleSlashesFromConfigNames()) {
            stream = stream.map(this::removeDoubleSlashesFromPath);
        }
        val list = stream.collect(Collectors.toList());
        if (list.isEmpty()) {
            throw new IllegalStateException("No valid configuration paths have been given.");
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * Tells whether double slashes need to be removed from configuration names.
     *
     * @return true/false
     * @see #getPaths()
     */
    protected boolean removeDoubleSlashesFromConfigNames() {
        return true;
    }

    /**
     * Default additional  {@link Stream#filter} used in {@link #createConfigPaths(Collection)} for potential filtering
     * out unwanted configuration names.
     *
     * @param path path
     * @return true/false
     */
    protected boolean sanitizePathFilter(String path) {
        return true;
    }

    /**
     * Creates configuration fetch context for specified {@code query}. This context is then passed to
     * implementation-specific methods.
     *
     * @param query config query
     * @return configuration fetch context.
     * @see #pathExists(String, Object)
     * @see #isDirectory(String, Object)
     * @see #listDirectory(String, Object)
     * @see #getFileNames(ConfigQuery, Object)
     * @see #loadConfig(String, Object)
     */
    protected abstract T createFetchContext(@NonNull ConfigQuery query);

    @Override
    protected List<Config> fetchConfigs(@NonNull ConfigQuery query) {
        log.debug("{} fetching configs for query: {}", this, query);
        val context = createFetchContext(query);
        log.debug("{} created fetch context: {}", this, context);

        // compute filenames to load
        val fileNames = getFileNames(query, context);

        // create load tasks
        val tasks = fileNames.stream()
            .map(fileName -> (Callable<Config>) () -> loadConfig(fileName, context))
            .collect(Collectors.toList());

        return runTasks(tasks, isParallel());
    }

    @Override
    protected void onFirstFetch() {
        super.onFirstFetch();

        val delimiter = "\n  - ";
        val locations = String.join(delimiter, getPaths());
        val logMsg = "{} configurations will be looked for in the following locations:{}{}";
        if (isVerbosePaths()) {
            log.info(logMsg, this, delimiter, locations);
        } else {
            log.debug(logMsg, this, delimiter, locations);
        }
    }

    /**
     * Loads configuration.
     *
     * @param path    configuration path
     * @param context configuration fetch context.
     * @return configuration
     */
    protected Config loadConfig(@NonNull String path, @NonNull T context) {
        log.trace("{} loading configuration for path: {} (context: {})", this, path, context);
        val config = openConfig(path, context)
            .map(reader -> readConfig(reader, path))
            .orElse(ConfigFactory.empty());
        return debugLoadedConfig(path, config);
    }

    /**
     * Opens configuration path.
     *
     * @param path    configuration file path
     * @param context configuration fetch context.
     * @return optional of reader
     * @throws NullPointerException in case of null arguments
     * @throws RuntimeException     if configuration cannot be opened for some reason
     */
    protected Optional<? extends Reader> openConfig(String path, T context) throws RuntimeException {
        throw new UnsupportedOperationException(
            "You should implement method openConfig(String path, T context) in class" + getClass().getName());
    }

    /**
     * Creates a stream of all possible filename paths where configuration should be looked up.
     *
     * @param query   config query
     * @param context configuration fetch context.
     * @return list of filenames
     * @throws NullPointerException in case of null arguments
     */
    protected List<String> getFileNames(@NonNull ConfigQuery query, @NonNull T context) {
        val paths = interpolateVarStrings(getPaths(), query);
        log.trace("{} retrieving filenames for paths: {}", this, paths);
        return paths.stream()
            .flatMap(name -> maybeListDirectory(name, context))
            .distinct()
            .collect(Collectors.toList());
    }

    private Stream<String> maybeListDirectory(@NonNull String path, @NonNull T context) {
        if (!pathExists(path, context)) {
            warnOrThrowOnMissingConfigLocation(path);
            return Stream.empty();
        } else if (isDirectory(path, context)) {
            return listDirectoryPaths(path, context);
        } else {
            return Stream.of(path);
        }
    }

    /**
     * Tells whether specified path is a directory.
     *
     * @param path    path
     * @param context configuration fetch context.
     * @return true/false
     * @throws NullPointerException in case of null arguments
     */
    protected abstract boolean isDirectory(@NonNull String path, T context);

    /**
     * Tells whether specified path exists.
     *
     * @param path    path
     * @param context configuration fetch context.
     * @return true/false
     * @throws NullPointerException in case of null arguments
     */
    protected abstract boolean pathExists(@NonNull String path, T context);

    /**
     * Lists directory.
     *
     * @param path    directory path
     * @param context configuration fetch context.
     * @return stream of directory contents in a form of basenames only.
     * @throws NullPointerException in case of null arguments
     */
    protected abstract Stream<String> listDirectory(@NonNull String path, T context);

    /**
     * Logs path existence at appropriate debug/trace level.
     *
     * @param path   config path
     * @param result existence result
     * @return provided {@code result}
     * @see #pathExists(String, Object)
     */
    protected final boolean debugPathExists(String path, boolean result) {
        log.trace("{} pathExists('{}'): {}", this, path, result);
        return result;
    }

    /**
     * Logs directory status at appropriate debug/trace level.
     *
     * @param path   config path
     * @param result is directory status
     * @return provided {@code result}
     */
    protected final boolean debugIsDirectory(String path, boolean result) {
        log.debug("{} isDirectory('{}'): {}", this, path, result);
        return result;
    }

    /**
     * Lists directory (uses {@link #listDirectory(String, Object)}), applies path concatenations, checks for valid
     * filenames and sorts resulting stream.
     *
     * @param path    directory path
     * @param context configuration fetch context.
     * @return Stream of directory contents
     * @throws NullPointerException in case of null arguments
     * @see #listDirectory(String, Object)
     */
    protected final Stream<String> listDirectoryPaths(@NonNull String path, T context) {
        val stream = listDirectory(path, context)
            .map(e -> path + "/" + e)
            .filter(this::isValidConfigName)
            .sorted();

        return (isConfdEnabled() && hasConfDotDir(path, context)) ?
            Stream.concat(stream, listDirectoryPaths(path + "/" + CONF_D_DIR, context)) : stream;
    }

    private boolean hasConfDotDir(String path, T context) {
        return isDirectory(path + "/" + CONF_D_DIR, context);
    }

    /**
     * Base implementation for {@link FilesystemLikeConfigSource} implementation builders.
     */
    public static abstract class Builder<T extends Builder<T>> extends ConfigSourceBuilder<T> {
        /**
         * Tells whether conf.d style loading is enabled.
         */
        @Getter
        private boolean confdEnabled = true;

        /**
         * Tells whether config source will log paths on first fetch.
         *
         * @see #onFirstFetch()
         * @see #getPaths()
         */
        @Getter
        private boolean verbosePaths = true;

        /**
         * Paths where configurations will be looked for.
         */
        @Getter
        private List<String> paths = new ArrayList<>();

        /**
         * Sets whether {@code conf.d}-style configuration loading is enabled.
         *
         * @param confdEnabled true/false
         * @return reference to itself
         */
        public T setConfdEnabled(boolean confdEnabled) {
            this.confdEnabled = confdEnabled;
            return getThis();
        }

        /**
         * Sets whether config source will verbosely log paths on first fetch.
         *
         * @param verbosePaths true/false
         * @see #onFirstFetch()
         */
        public T setVerbosePaths(boolean verbosePaths) {
            this.verbosePaths = verbosePaths;
            return getThis();
        }

        /**
         * Adds one or more configuration names. Configuration names can contain <b>${application}</b> or <b>${env}</b>
         * placeholders which are substituted before each configuration fetch.
         *
         * @param names one or more configuration names.
         * @return reference to itself
         */
        public T withPath(@NonNull String... names) {
            return withPaths(Arrays.asList(names));
        }

        /**
         * Adds one or more configuration paths. Configuration paths can contain <b>${application}</b> or <b>${env}</b>
         * placeholders which are substituted before each configuration fetch.
         *
         * @param names one or more configuration names.
         * @return reference to itself
         */
        public T withPaths(@NonNull Collection<String> names) {
            paths.addAll(names);
            return getThis();
        }

        /**
         * Sets config paths.
         *
         * @param paths config names
         * @return reference to itself
         */
        public T setPaths(@NonNull Collection<String> paths) {
            this.paths = new ArrayList<>(paths);
            return getThis();
        }

        @Override
        public void withConfig(Config config) {
            super.withConfig(config);

            cfgBoolean(config, "confd-enabled", this::setConfdEnabled);
            cfgBoolean(config, "warn-on-missing-paths", this::setWarnOnMissing);
            cfgBoolean(config, "fail-on-missing-paths", this::setFailOnMissing);
            cfgBoolean(config, "verbose-paths", this::setVerbosePaths);
            cfgExtract(config, "paths", Config::getStringList, this::setPaths);
        }

        @Override
        public T checkState() {
            val paths = Tsc4jImplUtils.toUniqueList(getPaths());
            if (paths.isEmpty()) {
                throw new IllegalStateException("At least one config loading path needs to be defined.");
            }
            return super.checkState();
        }
    }
}
