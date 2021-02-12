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

import com.github.tsc4j.core.CloseableInstance;
import com.github.tsc4j.core.CloseableReloadableConfig;
import com.github.tsc4j.core.Pair;
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
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.annotation.Nullable;
import javax.inject.Singleton;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * {@link PropertySourceLoader} that is able to bootstrap {@link Tsc4jPropertySource}.
 */
@Slf4j
@Singleton
public final class Tsc4jPropertySourceLoader extends CloseableInstance
    implements PropertySourceLoader, Toggleable, Ordered {
    static final int ORDER_POSITION = SystemPropertiesPropertySource.POSITION + 10_000;

    static final String DEFAULT_APP_NAME = "micronaut-application";
    static final String DEFAULT_DATACENTER_NAME = "default";

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
            val propertySource = Utils.instanceHolder()
                .getOrCreate(() -> instanceHolderCreator(env))
                .second();
            return Optional.ofNullable(propertySource);
        }

        return Optional.empty();
    }

    private Pair<CloseableReloadableConfig, Tsc4jPropertySource> instanceHolderCreator(@NonNull Environment env) {
        val rc = createReloadableConfig(env);
        val propertySource = new Tsc4jPropertySource(rc);
        return new Pair<>(rc, propertySource);
    }

    private CloseableReloadableConfig createReloadableConfig(Environment env) {
        val appName = getAppNameFromEnv(env);
        val envs = Tsc4jImplUtils.sanitizeEnvs(env.getActiveNames());

        val rc = ReloadableConfigFactory.defaults()
            .setAppName(appName)
            .setDatacenter(DEFAULT_DATACENTER_NAME) // TODO: figure out a way to determine this.
            .setEnvs(new ArrayList<>(envs))
            .create();
        log.debug("{} created reloadable config: {}", this, rc);

        // fetch config
        rc.getSync();
        log.debug("{} successfully fetched config from reloadable config: {} ", this, rc);

        return rc;
    }

    private static String getAppNameFromEnv(Environment env) {
        return env.getPropertySources().stream()
            .map(Tsc4jPropertySourceLoader::getAppNameFromPropertySource)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst()
            .orElse(DEFAULT_APP_NAME);
    }

    private static Optional<String> getAppNameFromPropertySource(PropertySource source) {
        return Optional.ofNullable(source.get(ApplicationConfiguration.APPLICATION_NAME))
            .map(Object::toString)
            .flatMap(Tsc4jImplUtils::optString);
    }

    @Override
    protected void doClose() {
        Utils.instanceHolder().close();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "@" + hashCode();
    }
}
