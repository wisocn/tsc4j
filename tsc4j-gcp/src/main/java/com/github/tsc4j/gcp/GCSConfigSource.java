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

package com.github.tsc4j.gcp;


import com.github.tsc4j.core.ConfigQuery;
import com.github.tsc4j.core.ConfigSource;
import com.github.tsc4j.core.FilesystemLikeConfigSource;
import com.github.tsc4j.core.Tsc4jCache;
import com.github.tsc4j.core.Tsc4jException;
import com.github.tsc4j.core.Tsc4jImplUtils;
import com.github.tsc4j.core.WithCache;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobListOption;
import com.typesafe.config.Config;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.val;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.cloud.storage.StorageOptions.getDefaultInstance;

/**
 * <a href="https://cloud.google.com/storage/">Google cloud storage</a> implementation of {@link ConfigSource}.
 */
public final class GCSConfigSource
    extends FilesystemLikeConfigSource<Map<String, Blob>>
    implements WithCache<String, Config> {
    private static final String GCS_URL_PREFIX = "gs://";
    private static final Pattern GCS_URL_PATTERN = Pattern.compile("^" + GCS_URL_PREFIX + "([\\w\\-\\.]+)/(.*)");
    static final String TYPE = "gcp.gcs";

    @Getter
    private final Tsc4jCache<String, Config> cache;

    private final Storage storage;

    /**
     * Creates new instance.
     */
    protected GCSConfigSource(Builder builder) {
        super(builder);
        this.storage = createStorage(builder);
        this.cache = Tsc4jImplUtils.newCache(toString(), builder.getCacheTtl(), builder.getClock());
    }

    private Storage createStorage(Builder builder) {
        return openCredentials(builder)
            .map(this::loadGoogleCredentials)
            .map(credentials -> getDefaultInstance().toBuilder().setCredentials(credentials).build().getService())
            .orElseGet(() -> getDefaultInstance().getService());
    }

    private Optional<InputStream> openCredentials(@NonNull Builder builder) {
        return openCredentialsFromString(builder)
            .map(Optional::of)
            .orElseGet(() -> openCredentialsFromFile(builder));
    }

    private Optional<InputStream> openCredentialsFromString(@NonNull Builder builder) {
        return Tsc4jImplUtils.optString(builder.getCredentialsString())
            .map(str -> {
                log.info("{} reading GCS credentials from string", this);
                return new ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8));
            });
    }

    private Optional<InputStream> openCredentialsFromFile(@NonNull Builder builder) {
        return Tsc4jImplUtils.optString(builder.getCredentialsFile())
            .flatMap(path -> {
                log.info("{} loading GCS credentials from file: {}", this, path);
                return Tsc4jImplUtils.openFromFilesystemOrClassPath(path);
            });
    }

    @SneakyThrows
    private GoogleCredentials loadGoogleCredentials(@NonNull InputStream is) {
        return GoogleCredentials.fromStream(is);
    }

    /**
     * Creates new instance builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    protected boolean sanitizePathFilter(@NonNull String path) {
        if (!path.startsWith(GCS_URL_PREFIX)) {
            log.warn("{} invalid GCS config path [syntax: 'gcs://bucket_name/path']: '{}'", this, path);
            return false;
        }
        return super.sanitizePathFilter(path);
    }

    @Override
    protected final boolean removeDoubleSlashesFromConfigNames() {
        return false;
    }

    @Override
    protected Map<String, Blob> createFetchContext(@NonNull ConfigQuery query) {
        val tasks = createFetchContextTasks(query);
        val results = runTasks(tasks, isParallel());

        val map = new LinkedHashMap<String, Blob>();
        results.forEach(e -> map.putAll(e));

        log.trace("{} created fetch context ctx: {}", this, map);
        return map;
    }

    private List<Callable<Map<String, Blob>>> createFetchContextTasks(@NonNull ConfigQuery query) {
        return interpolateVarStrings(getPaths(), query).stream()
            .map(this::toFetchContextTask)
            .collect(Collectors.toList());
    }

    private Callable<Map<String, Blob>> toFetchContextTask(@NonNull String gcsUrl) {
        return () -> fetchGsUrlSummary(gcsUrl);
    }

    @Override
    protected boolean isDirectory(@NonNull String path, @NonNull Map<String, Blob> context) {
        return context.keySet().stream()
            .anyMatch(url -> url.startsWith(path + "/"));
    }

    @Override
    protected boolean pathExists(@NonNull String path, @NonNull Map<String, Blob> context) {
        return context.keySet().stream()
            .anyMatch(url -> url.startsWith(path));
    }

    @Override
    protected Stream<String> listDirectory(@NonNull String gcsUrl, @NonNull Map<String, Blob> context) {
        val prefix = gcsUrl + "/";
        return context.keySet().stream()
            .filter(e -> e.startsWith(prefix))
            .map(e -> e.replace(gcsUrl, "").substring(1))
            .filter(e -> !e.isEmpty());
    }

    @Override
    protected Config loadConfig(@NonNull String gcsUrl, @NonNull Map<String, Blob> context) {
        val etag = Optional.ofNullable(context.get(gcsUrl))
            .map(blob -> blob.getEtag())
            .orElse("");
        val cacheKey = cacheKey(gcsUrl, etag);
        return getFromCache(cacheKey)
            .orElseGet(() -> putToCache(cacheKey, loadConfig(gcsUrl, context)));
    }

    @Override
    protected Optional<Reader> openConfig(@NonNull String gcsUrl, @NonNull Map<String, Blob> context) {
        return Optional.ofNullable(context.get(gcsUrl))
            .map(blob -> blob.getContent())
            .map(ByteArrayInputStream::new)
            .map(is -> new InputStreamReader(is, StandardCharsets.UTF_8));
    }

    private String cacheKey(String url, String etag) {
        return url + "|" + etag;
    }

    private Map<String, Blob> fetchGsUrlSummary(@NonNull String gcsUrl) {
        val bucketName = bucketName(gcsUrl);
        val path = bucketPath(gcsUrl);
        log.debug("{} fetching gcs summary for: {}", this, gcsUrl);

        try {
            val map = new LinkedHashMap<String, Blob>();
            Optional.ofNullable(storage.get(bucketName))
                .map(bucket -> bucket.list(BlobListOption.prefix(path)).iterateAll())
                .orElse(Collections.emptyList())
                .forEach(blob -> {
                    val url = GCS_URL_PREFIX + blob.getBucket() + "/" + blob.getName();
                    map.put(url, blob);
                });
            return map;
        } catch (Exception e) {
            throw Tsc4jException.of("Error fetching GCS url summary of %s: %%s", e, gcsUrl);
        }
    }

    private Matcher gcsUrlMatcher(@NonNull String gcsUrl) {
        val matcher = GCS_URL_PATTERN.matcher(gcsUrl);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Bad GCS URL: " + gcsUrl);
        }
        return matcher;
    }

    /**
     * Extracts bucket name from a gcs url
     *
     * @param gcsUrl gcs object url in a form of {@code gcs://bucketName/some/path}
     * @return gcs bucket name
     * @throws NullPointerException     in case of null arguments
     * @throws IllegalArgumentException if {@code gcsUrl} is not a valid s3 url
     */
    private String bucketName(@NonNull String gcsUrl) {
        return gcsUrlMatcher(gcsUrl).group(1);
    }

    /**
     * Extracts bucket name from a s3 url
     *
     * @param gcsUrl gcs object url in a form of {@code gcs://bucketName/some/path}
     * @return gcs bucket name
     * @throws NullPointerException     in case of null arguments
     * @throws IllegalArgumentException if {@code gcsUrl} is not a valid s3 url
     */
    private String bucketPath(@NonNull String gcsUrl) {
        return gcsUrlMatcher(gcsUrl).group(2);
    }

    /**
     * Builder for {@link GCSConfigSource}.
     */
    @Data
    @Accessors(chain = true)
    @EqualsAndHashCode(callSuper = false)
    public static class Builder extends FilesystemLikeConfigSource.Builder<Builder> {
        /**
         * Google credentials service account key filename (can be on filesystem or classpath).
         *
         * @see #getCredentialsString()
         */
        @Getter
        private String credentialsFile = null;

        /**
         * Google credentials service account key as string. This setting takes precedence over {@link
         * #getCredentialsFile()} if it's non-empty string.
         *
         * @see #getCredentialsFile()
         */
        @Getter
        private String credentialsString = null;

        @Override
        public Builder withConfig(@NonNull Config config) {
            configVal(config, "credentials-file", Config::getString).ifPresent(this::setCredentialsFile);
            configVal(config, "credentials-string", Config::getString).ifPresent(this::setCredentialsString);
            return super.withConfig(config);
        }

        @Override
        public ConfigSource build() {
            return new GCSConfigSource(this);
        }
    }
}
