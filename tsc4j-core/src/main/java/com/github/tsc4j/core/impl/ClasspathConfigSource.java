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


import com.github.tsc4j.core.ConfigQuery;
import com.github.tsc4j.core.ConfigSource;
import com.github.tsc4j.core.FilesystemLikeConfigSource;
import com.github.tsc4j.core.Tsc4jImplUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.NonNull;
import lombok.val;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Classpath implementation of {@link ConfigSource}.
 */
public final class ClasspathConfigSource extends FilesystemLikeConfigSource<String> {
    /**
     * Default configuration search list on classpath.
     */
    protected static final List<String> DEFAULT_CLASSPATH_PATHS = Collections.unmodifiableList(Arrays.asList(
        "/application.conf",
        "/application-${env}.conf",
        "/config/",
        "/config/application-${env}.conf",
        "/config/${env}",
        "/config/${application}/${env}"));

    static final String TYPE = "classpath";

    /**
     * Creates new instance with specified application name and configuration classpath prefixes set to
     * {@link #DEFAULT_CLASSPATH_PATHS}.
     *
     * @throws NullPointerException in case of null arguments
     * @see #DEFAULT_CLASSPATH_PATHS
     */
    public ClasspathConfigSource() {
        this(DEFAULT_CLASSPATH_PATHS);
    }

    /**
     * <p>Creates instance with one or more configuration prefixes.</p>
     * <p>{@code classpathPrefix} contain <b>${application}</b> or <b>${env}</b>
     * placeholders which are substituted before each configuration fetch.</p>
     *
     * @param classpathPrefix classpath configuration path prefixes (subdirectories)
     * @throws NullPointerException in case of null arguments
     */
    public ClasspathConfigSource(@NonNull String... classpathPrefix) {
        this(Arrays.asList(classpathPrefix));
    }

    /**
     * <p>Creates instance with one or more configuration names.</p>
     * <p>{@code classpathPrefixes} can contain <b>${application}</b> or <b>${env}</b>
     * placeholders which are substituted before each configuration fetch.</p>
     *
     * @param classpathPrefixes classpath configuration path prefixes (subdirectories)
     * @throws NullPointerException in case of null arguments
     */
    public ClasspathConfigSource(@NonNull Collection<String> classpathPrefixes) {
        this(builder().withPaths(classpathPrefixes));
    }

    /**
     * Creates new instance.
     */
    protected ClasspathConfigSource(Builder builder) {
        super(builder);
    }

    /**
     * Creates new instance builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates new instance builder with default configuration already applied.
     */
    public static Builder defaultBuilder() {
        return builder().withPaths(DEFAULT_CLASSPATH_PATHS);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    protected boolean isParallel() {
        return false;
    }

    @Override
    protected boolean isDirectory(@NonNull String path, String context) {
        val result = Optional.ofNullable(getClass().getResource(path))
            .map(url -> new File(url.getFile()))
            .map(file -> file.exists() && file.isDirectory())
            .orElse(false);
        return debugIsDirectory(path, result);
    }

    @Override
    protected boolean pathExists(@NonNull String path, String context) {
        val result = Optional.ofNullable(getClass().getResource(path)).isPresent();
        return debugPathExists(path, result);
    }

    @Override
    protected Stream<String> listDirectory(@NonNull String path, String context) {
        return openFromClasspath(path)
            .map(reader -> {
                val items = reader.lines().collect(Collectors.toList());
                Tsc4jImplUtils.close(reader, log);
                return items.stream();
            })
            .orElse(Stream.empty());
    }

    @Override
    protected Optional<? extends Reader> openConfig(String path, String context) throws RuntimeException {
        throw new UnsupportedOperationException("This method is not implemented in " + getClass().getName());
    }

    @Override
    protected String createFetchContext(ConfigQuery query) {
        return "";
    }

    @Override
    protected Config loadConfig(String path, String context) {
        log.debug("{} loading: {}", this, path);

        // remove slash from beginning of the path because ConfigFactory.parseResources()
        // returns empty configuration object with path starts with "/"
        if (path.startsWith("/") && path.length() > 1) {
            path = path.substring(1);
        }

        val config = ConfigFactory.parseResources(path);
        return debugLoadedConfig(path, config);
    }

    private Optional<BufferedReader> openFromClasspath(@NonNull String path) {
        return Tsc4jImplUtils.openFromClassPath(path)
            .map(is -> new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)));
    }

    /**
     * Builder for {@link ClasspathConfigSource}.
     */
    public static final class Builder extends FilesystemLikeConfigSource.Builder<Builder> {
        public Builder() {
            setWarnOnMissing(false);
            setPaths(DEFAULT_CLASSPATH_PATHS);
        }

        @Override
        public String type() {
            return "classpath";
        }

        @Override
        public String description() {
            return "Loads HOCON files from classpath.";
        }

        @Override
        public Class<? extends ConfigSource> creates() {
            return ClasspathConfigSource.class;
        }

        @Override
        public ConfigSource build() {
            return new ClasspathConfigSource(this);
        }
    }
}
