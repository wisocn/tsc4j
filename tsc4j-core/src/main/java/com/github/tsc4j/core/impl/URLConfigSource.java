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

import com.github.tsc4j.core.AbstractConfigSource;
import com.github.tsc4j.core.ConfigQuery;
import com.github.tsc4j.core.ConfigSource;
import com.github.tsc4j.core.ConfigSourceBuilder;
import com.github.tsc4j.core.Tsc4jException;
import com.github.tsc4j.core.Tsc4jImplUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.val;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.FileNotFoundException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * {@link ConfigSource} that fetches configurations using HTTP protocol.
 */
public final class URLConfigSource extends AbstractConfigSource {
    static final String TYPE = "url";

    /**
     * HTTP request method
     */
    private final String method;

    /**
     * List of URL patterns to fetch configuration from.
     */
    private final List<String> urls;

    /**
     * Additional headers to send.
     */
    private final Map<String, String> headers;

    private final boolean verifyTls;
    private final int timeoutMillis;

    private static final HostnameVerifier insecureHostnameVerifier = (hostname, session) -> true;
    private static final SSLContext insecureTlsCtx = createInsecureTlsContext();

    /**
     * Creates new instance.
     *
     * @param builder instance builder.
     */
    protected URLConfigSource(@NonNull Builder builder) {
        super(builder);
        this.method = Tsc4jImplUtils.optString(builder.getMethod()).orElseThrow(IllegalArgumentException::new);
        this.urls = Tsc4jImplUtils.toUniqueList(builder.getUrls());
        this.headers = createHeaders(builder);
        this.verifyTls = builder.isVerifyTLS();
        this.timeoutMillis = (int) builder.getTimeout().toMillis();
        //this.httpClient = HttpClientUtils.createHttpClient(builder.isVerifyTLS());
    }

    private Map<String, String> createHeaders(@NonNull Builder builder) {
        if (builder.getHeaders().isEmpty() && builder.getUsername() == null) {
            return Collections.emptyMap();
        }

        val map = new LinkedHashMap<String, String>();
        builder.getHeaders().forEach((k, v) -> map.put(k.toLowerCase(), v));

        // HTTP basic auth?
        if (builder.getUsername() != null) {
            val credentials = builder.getUsername() + ":" + builder.getPassword();
            val credentialsBase64 = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            map.put("authorization", "basic " + credentialsBase64);
        }

        return Collections.unmodifiableMap(map);
    }

    @Override
    public String getType() {
        return TYPE;
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
    protected List<Config> fetchConfigs(@NonNull ConfigQuery query) {
        val tasks = createFetchTasks(query);
        return runTasks(tasks, isParallel());
    }

    private List<Callable<Config>> createFetchTasks(@NonNull ConfigQuery query) {
        return interpolateVarStrings(urls, query).stream()
            .map(this::toFetchTask)
            .collect(Collectors.toList());
    }

    private Callable<Config> toFetchTask(@NonNull String url) {
        return () -> fetchConfig(url);
    }

    private Config fetchConfig(@NonNull String url) {
        try {
            val conn = openConnection(new URL(url));
            return readConfig(conn.getInputStream(), url);
        } catch (FileNotFoundException e) {
            warnOrThrowOnMissingConfigLocation(url);
            return ConfigFactory.empty();
        } catch (Exception e) {
            throw Tsc4jException.of("Error fetching config from url %s: %%s", e, url);
        }
    }

    @SneakyThrows
    private HttpURLConnection openConnection(@NonNull URL url) {
        log.debug("{} fetching configuration from: {} ", this, url);
        val conn = maybeDisableTLSVerification((HttpURLConnection) url.openConnection());

        conn.setRequestMethod(method);
        conn.setConnectTimeout(timeoutMillis);
        conn.setReadTimeout(timeoutMillis);

        headers.forEach(conn::setRequestProperty);

        return conn;
    }

    /**
     * Disables TLS validation on a specified connection if {@link #verifyTls} is false.
     *
     * @param conn connection
     * @return connection
     */
    private HttpURLConnection maybeDisableTLSVerification(@NonNull HttpURLConnection conn) {
        if (!verifyTls && conn instanceof HttpsURLConnection) {
            val httpsConn = (HttpsURLConnection) conn;
            httpsConn.setSSLSocketFactory(insecureTlsCtx.getSocketFactory());
            httpsConn.setHostnameVerifier(insecureHostnameVerifier);
            log.debug("{} disabled TLS verification on: {}", this, httpsConn);
        }

        return conn;
    }

    @SneakyThrows
    private static SSLContext createInsecureTlsContext() {
        final TrustManager[] insecureTrustManagers = new TrustManager[]{insecureTrustManager()};

        // create context
        val ctx = SSLContext.getInstance("SSL");
        ctx.init(null, insecureTrustManagers, new SecureRandom());

        return ctx;
    }

    private static TrustManager insecureTrustManager() {
        return new X509TrustManager() {
            @Override
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            @Override
            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
        };
    }

    /**
     * Builder for {@link URLConfigSource}
     */
    @Data
    @Accessors(chain = true)
    @EqualsAndHashCode(callSuper = true)
    public static class Builder extends ConfigSourceBuilder<Builder> {
        /**
         * Use the following request method
         */
        private String method = "GET";

        /**
         * List of URL address patterns (can contain magic variables)
         */
        private List<String> urls = new ArrayList<>();

        /**
         * HTTP basic auth username.
         */
        private String username;

        /**
         * HTTP basic auth password.
         */
        private String password;

        /**
         * Additional headers.
         */
        private Map<String, String> headers = new HashMap<>();

        /**
         * Verify TLS certificates?
         */
        private boolean verifyTLS = true;

        /**
         * HTTP request timeout.
         */
        private Duration timeout = Duration.ofSeconds(10);

        /**
         * Sets single HTTP header.
         *
         * @param name  header name
         * @param value header value
         * @return reference to itself
         * @throws NullPointerException     in case of null arguments
         * @throws IllegalArgumentException if values are empty
         */
        public Builder header(@NonNull String name, @NonNull String value) {
            name = name.trim();
            value = value.trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Header name is empty.");
            }
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Header value is empty.");
            }
            headers.put(name, value);
            return getThis();
        }

        /**
         * Adds single url.
         *
         * @param url url pattern
         * @return reference to itself.
         */
        public Builder url(@NonNull String url) {
            url = url.trim();
            if (url.isEmpty()) {
                throw new IllegalArgumentException("Cannot add empty url.");
            }
            urls.add(url);
            return getThis();
        }

        @Override
        public Builder withConfig(Config config) {
            configVal(config, "method", Config::getString).ifPresent(this::setMethod);
            configVal(config, "urls", Config::getStringList).ifPresent(this::setUrls);
            configVal(config, "username", Config::getString).ifPresent(this::setUsername);
            configVal(config, "password", Config::getString).ifPresent(this::setPassword);
            configVal(config, "headers", Config::getObject)
                .ifPresent(e -> e.unwrapped().forEach((key, val) -> header(key, val.toString())));
            configVal(config, "verify-tls", Config::getBoolean).ifPresent(this::setVerifyTLS);
            return super.withConfig(config);
        }

        @Override
        protected Builder checkState() {
            // check urls
            if (urls.isEmpty()) {
                throw new IllegalArgumentException("No urls to fetch configuration from were set");
            }
            val hasBadUrls = urls.stream()
                .anyMatch(e -> !e.startsWith("http://") || e.startsWith("https://"));
            if (hasBadUrls) {
                throw new IllegalArgumentException("URLs contain bad urls: " + urls);
            }

            if ((username != null && password == null) || (username == null && password != null)) {
                throw new IllegalArgumentException("Both username and password need to be specified, or none of them.");
            }

            Tsc4jImplUtils.optString(getMethod()).orElseThrow(() -> new IllegalArgumentException("HTTP request method must be set."));

            return super.checkState();
        }

        @Override
        public ConfigSource build() {
            return new URLConfigSource(this);
        }
    }
}
