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

package com.github.tsc4j.credstash;

import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import com.github.tsc4j.aws.common.AwsConfig;
import com.github.tsc4j.aws.common.WithAwsConfig;
import com.github.tsc4j.aws.sdk1.AwsSdk1Utils;
import com.github.tsc4j.aws.sdk1.ParameterStoreValueProvider;
import com.github.tsc4j.core.AbstractConfigValueProvider;
import com.github.tsc4j.core.Tsc4jCache;
import com.github.tsc4j.core.Tsc4jException;
import com.github.tsc4j.core.Tsc4jImplUtils;
import com.github.tsc4j.core.ValueProviderBuilder;
import com.github.tsc4j.core.WithCache;
import com.jessecoyle.CredStashBouncyCastleCrypto;
import com.jessecoyle.JCredStash;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.val;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Value provider that provides values from <a href="https://github.com/fugue/credstash">credstash secrets store</a>.
 */
public final class CredstashConfigValueProvider extends AbstractConfigValueProvider implements WithCache<String, String> {
    static final String TYPE = "credstash";
    static final String DEFAULT_TABLE_NAME = "credential-store";

    private final JCredStash credstash;
    private final String tableName;
    private final Map<String, String> encryptionContext;
    @Getter
    private final Tsc4jCache<String, String> cache;

    /**
     * Creates new instance.
     *
     * @param builder instance builder
     */
    protected CredstashConfigValueProvider(@NonNull Builder builder) {
        this(builder, createCredstash(builder));
    }

    /**
     * Creates new instance.
     *
     * @param builder   builder
     * @param credstash credstash instance
     */
    protected CredstashConfigValueProvider(@NonNull Builder builder, @NonNull JCredStash credstash) {
        super(builder.getName(), builder.isAllowMissing(), builder.isParallel());
        this.credstash = credstash;
        this.tableName = builder.getTableName();
        this.encryptionContext = Collections.unmodifiableMap(new LinkedHashMap<>(builder.getEncryptionContext()));
        this.cache = Tsc4jImplUtils.newCache(toString(), builder.getCacheTtl(), builder.getClock());
    }

    /**
     * Creates credstash instance from a builder.
     *
     * @param b builder
     * @return credstash instance
     * @see #createCredstash(String, AwsConfig)
     */
    private static JCredStash createCredstash(@NonNull Builder b) {
        return createCredstash(b.getTableName(), b.getAwsConfig());
    }


    /**
     * Creates credstash instance.
     *
     * @param tableName table name
     * @param config    aws config
     * @return credstash instance.
     */
    static JCredStash createCredstash(@NonNull String tableName, @NonNull AwsConfig config) {
        val credentialProvider = AwsSdk1Utils.getCredentialsProvider(config);
        val regionProvider = AwsSdk1Utils.getRegionProvider(config);
        return new JCredStash(tableName.trim(), credentialProvider, regionProvider, new CredStashBouncyCastleCrypto());
    }

    static Supplier<JCredStash> createCredstashSupplier(@NonNull String tableName, @NonNull AwsConfig config) {
        return () -> createCredstash(tableName, config);
    }

    /**
     * Creates new instance builder.
     *
     * @return instance builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected Map<String, ConfigValue> doGet(List<String> names) {
        val res = new LinkedHashMap<String, ConfigValue>();
        runTasks(createFetchTasks(names), isParallel())
            .stream()
            .forEach(e -> res.putAll(e));
        return res;
    }

    /**
     * Creates fetch tasks for specified credential names.
     *
     * @param credentialNames credential names
     * @return list of callable tasks
     */
    private List<Callable<Map<String, ConfigValue>>> createFetchTasks(@NonNull Collection<String> credentialNames) {
        return credentialNames.stream()
            .map(name -> (Callable<Map<String, ConfigValue>>) () -> getCredential(name))
            .collect(Collectors.toList());
    }

    /**
     * Fetches credential secret, consulting cache if enabled..
     *
     * @param credentialName credential name.
     * @return optional of fetched credential
     */
    protected Map<String, ConfigValue> getCredential(@NonNull String credentialName) {
        return doGetCredential(credentialName)
            .map(e -> Collections.singletonMap(credentialName, ConfigValueFactory.fromAnyRef(e)))
            .orElse(Collections.emptyMap());
    }

    private Optional<String> doGetCredential(@NonNull String credentialName) {
        try {
            return getFromCache(credentialName)
                .map(Optional::of)
                .orElseGet(() -> getCredentialFromCredstash(credentialName));
        } catch (Tsc4jException e) {
            throw e;
        } catch (Exception e) {
            throw Tsc4jException.of("Error fetching credstash credential '%s': %%s", e, credentialName);
        }
    }

    /**
     * Fetches specific credential from credstash.
     *
     * @param credentialName credential name.
     * @return optional of fetched credential
     */
    private Optional<String> getCredentialFromCredstash(@NonNull String credentialName) {
        val name = fixCredentialName(credentialName);
        if (name.isEmpty()) {
            return Optional.empty();
        }

        log.debug("{} fetching credential from credstash: '{}'", this, name);
        try {
            val secret = credstash.getSecret(name, encryptionContext);
            return Optional.ofNullable(secret)
                .map(s -> putToCache(name, s));
        } catch (ResourceNotFoundException e) {
            throw Tsc4jException.of("Cannot read credstash table '%s': %%s", e, tableName);
        } catch (RuntimeException e) {
            if (isNotFoundException(e)) {
                if (allowMissing()) {
                    log.warn("{} credstash doesn't contain credential: '{}'", this, name);
                    return Optional.empty();
                } else {
                    throw Tsc4jException.of("Credstash credential doesn't exist: %s", e, name);
                }
            }
            throw e;
        }
    }

    /**
     * Fixes credential name, will be removed from future release.
     *
     * @param credentialName credential name
     * @return sanitized credential name.
     */
    private String fixCredentialName(String credentialName) {
        // TODO: remove `credential=` replacement in credential name
        return credentialName.trim()
            .replaceFirst("^credential=", "")
            .trim();
    }

    /**
     * Tells whether exception looks like {@link JCredStash} credential not found exception.
     *
     * @param e exception
     * @return true/false
     * @see JCredStash#getSecret(String, Map)
     * @see JCredStash#readDynamoItem(String, String)
     */
    private boolean isNotFoundException(@NonNull RuntimeException e) {
        val msg = e.getMessage();
        return msg.startsWith("Secret ") && msg.endsWith(" could not be found");
    }

    @Override
    public String getType() {
        return TYPE;
    }

    /**
     * Builder for {@link ParameterStoreValueProvider}.
     */
    @Data
    @Accessors(chain = true)
    @EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
    public static class Builder extends ValueProviderBuilder<Builder> implements WithAwsConfig<Builder> {
        @Getter
        private AwsConfig awsConfig = new AwsConfig();

        /**
         * Credstash dynamoDB table name.
         */
        private String tableName = DEFAULT_TABLE_NAME;

        /**
         * AWS KMS encryption context map.
         *
         * @see <a href="https://docs.aws.amazon.com/kms/latest/developerguide/encryption-context.html">AWS KMS
         *     encryption context</a>
         */
        private Map<String, String> encryptionContext = Collections.emptyMap();

        /**
         * Sets encryption context from a {@link Config} instance.
         *
         * @param cfg config instance.
         * @return reference to itself
         * @see #setEncryptionContext(Map)
         */
        public Builder withEncryptionContext(@NonNull Config cfg) {
            val map = new LinkedHashMap<String, String>();
            cfg.root().forEach((key, value1) -> {
                val value = value1.unwrapped();
                if (value != null) {
                    map.put(key, value.toString());
                }
            });
            return setEncryptionContext(map);
        }

        @Override
        protected Duration defaultCacheTtl() {
            return Duration.ofMinutes(15);
        }

        @Override
        protected Builder checkState() {
            val tn = getTableName().trim();
            if (tn.isEmpty()) {
                throw new IllegalStateException("dynamoDb table name cannot be empty.");
            }
            return super.checkState();
        }

        @Override
        public Builder withConfig(@NonNull Config cfg) {
            getAwsConfig().withConfig(cfg);

            configVal(cfg, "table-name", Config::getString).ifPresent(this::setTableName);
            configVal(cfg, "encryption-context", Config::getConfig).ifPresent(this::withEncryptionContext);

            return super.withConfig(cfg);
        }

        @Override
        public CredstashConfigValueProvider build() {
            return new CredstashConfigValueProvider(this);
        }
    }
}
