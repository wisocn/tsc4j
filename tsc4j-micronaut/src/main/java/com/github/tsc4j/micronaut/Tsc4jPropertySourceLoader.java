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

package com.github.tsc4j.micronaut;

import com.github.tsc4j.api.ReloadableConfig;
import com.github.tsc4j.core.CloseableInstance;
import com.github.tsc4j.core.CloseableReloadableConfig;
import com.github.tsc4j.core.ReloadableConfigFactory;
import com.github.tsc4j.core.Tsc4jImplUtils;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourceLoader;
import io.micronaut.context.env.SystemPropertiesPropertySource;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.Toggleable;
import io.micronaut.runtime.ApplicationConfiguration;
import lombok.NonNull;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.annotation.Nullable;
import javax.inject.Singleton;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link PropertySourceLoader} that is able to bootstrap {@link Tsc4jPropertySource}.
 */
@Slf4j
@Singleton
public final class Tsc4jPropertySourceLoader extends CloseableInstance
    implements PropertySourceLoader, Toggleable, Ordered {
    static final int ORDER_POSITION = SystemPropertiesPropertySource.POSITION + 10_000;

    private final AtomicBoolean returned = new AtomicBoolean();

    private static volatile CloseableReloadableConfig reloadableConfig;
    private volatile Tsc4jPropertySource propertySource;

    /**
     * Returns reloadable config.
     *
     * @return reloadable config
     * @throws IllegalStateException if reloadable config is not set
     */
    @Synchronized
    static ReloadableConfig getReloadableConfig() {
        val rc = reloadableConfig;
        if (rc == null) {
            throw new IllegalStateException("Reloadable config is not set.");
        }
        return rc;
    }

    /**
     * Cleans up instance
     */
    @Synchronized
    static void cleanup() {
        val rc = reloadableConfig;
        if (rc != null) {
            rc.close();
            reloadableConfig = null;
        }
    }

    @Override
    public int getOrder() {
        return ORDER_POSITION;
    }

    @Override
    public Map<String, Object> read(String name, InputStream input) {
        log.warn("invoked non-implemented read(name, stream)", name, input, new RuntimeException("Stacktrace"));
        return Collections.emptyMap();
    }

    @Override
    @SuppressWarnings("deprecation")
    public Optional<PropertySource> load(@NonNull String resourceName,
                                         @NonNull ResourceLoader resourceLoader,
                                         @Nullable String environmentName) {
        log.trace("{} resource name: {}, loader: {}, env: {}", this, resourceName, resourceLoader, environmentName);
        if (!resourceName.equals(Environment.DEFAULT_NAME)) {
            return Optional.empty();
        }

        log.debug("{} resource name: {}, loader: {}, env: {}", this, resourceName, resourceLoader, environmentName);
        if (resourceLoader instanceof Environment) {
            val env = (Environment) resourceLoader;
            return getOrCreatePropertySource(env);
        }

        return Optional.empty();
    }

    @Synchronized
    private Optional<PropertySource> getOrCreatePropertySource(@NonNull Environment env) {
        return Optional.ofNullable((PropertySource) this.propertySource)
            .map(Optional::of)
            .orElseGet(() -> createPropertySource(env));
    }

    private Optional<PropertySource> createPropertySource(Environment env) {
        val appName = env.getPropertySources().stream()
            .map(this::getAppNameFromPropertySource)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst()
            .orElse("micronaut-application");
        val envs = env.getActiveNames();

        val source = createPropertySource(appName, envs);
        return Optional.ofNullable(source);
    }

    private PropertySource createPropertySource(String appName, Collection<String> envs) {
        log.debug("{} creating micronaut property source for app '{}' with envs: {}", this, appName, envs);

        // TODO: datacenter name should not be fixed
        val rc = getOrCreateReloadableConfig(appName, "default", envs);

        // wait for config fetch to complete
        rc.getSync();

        val source = new Tsc4jPropertySource(rc);

        // side-effect: assign it...
        this.propertySource = source;

        log.debug("{} created micronaut property source: {}", this, source);
        return source;
    }

    private Optional<String> getAppNameFromPropertySource(@NonNull PropertySource source) {
        return Optional.ofNullable(source.get(ApplicationConfiguration.APPLICATION_NAME))
            .map(Object::toString)
            .flatMap(Tsc4jImplUtils::optString);
    }

    @Synchronized
    private ReloadableConfig getOrCreateReloadableConfig(String appName, String dataCenter, Collection<String> envs) {
        if (reloadableConfig == null) {
            reloadableConfig = ReloadableConfigFactory.defaults()
                .setAppName(appName)
                .setDatacenter(dataCenter)
                .setEnvs(new ArrayList<>(envs))
                .create();
            reloadableConfig.getSync();
            log.debug("{} created reloadable config: {}", this, reloadableConfig);
        }
        log.debug("{} returning reloadable config: {}", this, reloadableConfig);
        return reloadableConfig;
    }

    @Override
    protected void doClose() {
        // close instances
        Optional.ofNullable(reloadableConfig).ifPresent(Tsc4jImplUtils::close);
        Optional.ofNullable(propertySource).ifPresent(Tsc4jImplUtils::close);

        // clean fields
        returned.compareAndSet(true, false);
        reloadableConfig = null;
        propertySource = null;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "@" + hashCode();
    }
}
