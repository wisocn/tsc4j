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
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.NonNull;
import lombok.val;

import java.io.File;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * {@link ConfigSource} implementation that is able to load configurations from filesystem.
 */
public final class FilesConfigSource extends FilesystemLikeConfigSource<String> {
    static final String TYPE = "files";

    /**
     * Creates new instance.
     *
     * @param builder builder
     * @throws NullPointerException  in case of null arguments
     * @throws IllegalStateException in case of bad builder state
     */
    protected FilesConfigSource(Builder builder) {
        super(builder);
    }

    /**
     * Creates new instance builder.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
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
    protected String createFetchContext(ConfigQuery query) {
        return "";
    }

    @Override
    protected Config loadConfig(String path, String context) {
        log.debug("{} loading: {}", this, path);
        val config = ConfigFactory.parseFile(new File(path));
        return debugLoadedConfig(path, config);
    }

    @Override
    protected boolean isDirectory(@NonNull String path, String context) {
        val file = new File(path);
        val result = file.isDirectory() && file.canRead();
        return debugIsDirectory(path, result);
    }

    @Override
    protected boolean pathExists(@NonNull String path, String context) {
        val file = new File(path);
        val result = file.exists() && file.canRead();
        return debugPathExists(path, result);
    }

    @Override
    protected Stream<String> listDirectory(@NonNull String path, String context) {
        return Optional.ofNullable(new File(path).list())
            .map(Stream::of)
            .orElse(Stream.empty());
    }

    /**
     * Builder for {@link FilesConfigSource}.
     */
    public static class Builder extends FilesystemLikeConfigSource.Builder<Builder> {
        @Override
        public ConfigSource build() {
            return new FilesConfigSource(this);
        }
    }
}
