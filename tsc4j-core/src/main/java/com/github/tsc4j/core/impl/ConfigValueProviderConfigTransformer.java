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

import com.github.tsc4j.core.AbstractConfigTransformer;
import com.github.tsc4j.core.ConfigTransformer;
import com.github.tsc4j.core.ConfigTransformerBuilder;
import com.github.tsc4j.core.ConfigValueProvider;
import com.github.tsc4j.core.Tsc4j;
import com.github.tsc4j.core.Tsc4jImplUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;
import com.typesafe.config.ConfigValueType;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.github.tsc4j.core.Tsc4jImplUtils.optString;

/**
 * {@link ConfigTransformer} implementation that wraps collection of {@link ConfigValueProvider} instances and replaces
 * magic variable placeholders in config values with results of {@link ConfigValueProvider#get(Collection)} results.
 */
public final class ConfigValueProviderConfigTransformer
    extends AbstractConfigTransformer<ConfigValueProviderConfigTransformer.Context> {

    private static final List<Pattern> variableFormats = Collections.unmodifiableList(Arrays.asList(
        Pattern.compile("^([^\\|]+)(?:\\|([^\\|]+))?\\|(.+)"),
        Pattern.compile("^([\\w\\.\\-]+)(?::([\\w\\-\\.]+))?:(?://)?(.+)")
        // credstash://foo.bar, awsssm://foo/bar, consul://foo/bar
    ));

    private final List<ConfigValueProvider> providers;

    private final OnlyOnce<String> onlyOnce = new OnlyOnce<>(
        it -> log.warn("{} can't find registered config value provider: {}", this, it), 1000);

    /**
     * Creates new instance from builder
     *
     * @param builder config transformer builder
     */
    protected ConfigValueProviderConfigTransformer(@NonNull Builder builder) {
        super(builder);
        this.providers = createProviderList(builder.getConfigValueProviders());
    }

    private <T> List<T> createProviderList(List<T> c) {
        val list = c.stream()
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        return Collections.unmodifiableList(list);
    }

    /**
     * Creates new instance builder.
     *
     * @return instance builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected Context createTransformationContext(@NonNull Config config) {
        // scan config for variables
        val ctx = new Context();
        Tsc4jImplUtils.scanConfigObject(config.root(), (path, value) -> visitConfigEntry(path, value, ctx));

        // fetch updated values from value providers
        return updateTransformationContext(ctx);
    }

    /**
     * Visits config entry and registers updatable value to transformation context.
     *
     * @param path  config path
     * @param value config value at path
     * @param ctx   transformation context
     */
    private void visitConfigEntry(@NonNull String path, @NonNull ConfigValue value, @NonNull Context ctx) {
        // only config strings can be replaced with ConfigValues from ConfigValueProviders
        if (value.valueType() != ConfigValueType.STRING) {
            return;
        }

        val strValue = value.unwrapped().toString();
        scanVarStr(new VarStr(strValue), path, value, ctx);
    }

    private void scanVarStr(@NonNull VarStr varStr,
                            @NonNull String path,
                            @NonNull ConfigValue value,
                            @NonNull Context ctx) {
        varStr.scan(variable -> registerUpdatableValue(path, value, ctx, variable));
    }

    private void registerUpdatableValue(@NonNull String path,
                                        @NonNull ConfigValue value,
                                        @NonNull Context ctx,
                                        @NonNull String variable) {
        //log.trace("{} found value variable in config path '{}': {}", this, path, variable);
        val uv = new UpdatableConfigValue(path, variable, value);
        log.trace("{} created new updatable value: {}", this, uv);
        ctx.registerUpdatableValue(uv);
    }

    /**
     * Updates transformation context by fetching values from value providers.
     *
     * @param ctx transformation context.
     * @return transformation context
     */
    private Context updateTransformationContext(@NonNull Context ctx) {
        val updateCtx = new UpdateContext();
        ctx.paths().stream()
            .flatMap(path -> ctx.values(path).stream())
            .forEach(uv -> registerValueProviderFetchTask(uv, updateCtx));

        log.debug("{} updating transformation context: {}", this, ctx);

        // create and execute provider tasks
        val tasks = updateCtx.createPerValueProviderFetchTasks();
        log.trace("{} created {} value provider tasks", this, tasks.size());
        val results = runTasks(tasks, isParallel());
        log.debug("{} task execution returned {} results.", this, results.size());
        log.trace("{} updated transformation context: {}", this, ctx);

        return ctx;
    }

    private void registerValueProviderFetchTask(@NonNull UpdatableConfigValue uv, @NonNull UpdateContext ctx) {
        val valueSpec = getValueSpec(uv.variable);
        log.trace("{} found value spec for '{}': {}", this, uv.variable, valueSpec);

        // get value provider and register fetch task
        getValueProvider(valueSpec.valueProviderType, valueSpec.valueProviderName)
            .map(it -> {
                log.debug("{} will ask for '{}' (provider: {}, uv: {})", this, valueSpec.configValueName, it, uv);
                ctx.registerTask(it, valueSpec.configValueName, uv);
                return it;
            })
            .orElseGet(() -> {
                val desc = "'" + valueSpec.valueProviderType + ":" + valueSpec.valueProviderName + "'";
                if (allowErrors()) {
                    onlyOnce.add(desc);
                } else {
                    throw new IllegalStateException("Cannot find config value provider: " + desc);
                }

                // no other way to better handle absent cases.
                return null;
            });
    }

    /**
     * Returns provider by it's type or name.
     *
     * @param type provider type
     * @param name provider name
     * @return value provider
     * @throws com.github.tsc4j.core.Tsc4jException if provider cannot be found
     */
    private Optional<ConfigValueProvider> getValueProvider(@NonNull String type, @NonNull String name) {
        val anyNameIsOkay = name.isEmpty();
        return providers.stream()
            .filter(e -> e.getType().equals(type))
            .filter(e -> anyNameIsOkay || e.getName().equals(name))
            .findFirst();
    }

    private ValueSpec getValueSpec(@NonNull String str) {
        val sanitized = str.trim();
        return variableFormats.stream()
            .map(it -> it.matcher(sanitized))
            .filter(Matcher::find)
            .map(this::toValueProviderVariable)
            .findFirst()
            .orElseThrow(() -> badValueArg(str, "Unknown format."));
    }

    private ValueSpec toValueProviderVariable(Matcher m) {
        val type = optString(m.group(1)).orElse("");
        val name = optString(m.group(2)).orElse("");
        val valueName = optString(m.group(3)).orElse("");

        if (log.isTraceEnabled()) {
            log.trace("toValueProviderVariable[{}]: type '{}', name '{}', value-name: '{}'",
                m.group(0), type, name, valueName);
        }

        if (type.isEmpty()) {
            throw badValueArg("type", m.group());
        }

        if (valueName.isEmpty()) {
            throw badValueArg("value-name", "can't use variable.");
        }

        return new ValueSpec(type, name, valueName);
    }

    private IllegalArgumentException badValueArg(String value, String errDesc) {
        return new IllegalArgumentException(String.format(
            "Bad value declaration '%s': %s (valid syntax: <value-provider-type>[:value-provider-name]://<value-specification>)",
            value, errDesc
        ));
    }

    @Override
    protected ConfigValue transformString(@NonNull String path, @NonNull ConfigValue value, @NonNull Context ctx) {
        return Optional.of(new VarStr(value.unwrapped().toString()))
            .flatMap(varStr -> findUpdatedConfigValue(varStr, path, ctx))
            .orElse(value);
    }

    private Optional<ConfigValue> findUpdatedConfigValue(@NonNull VarStr varStr,
                                                         @NonNull String path,
                                                         @NonNull Context ctx) {
        if (varStr.isEmpty()) {
            return Optional.empty();
        }

        // if there are more than one magic vars in the varstr, we're going to stringify updated values
        if (varStr.count() > 1) {
            val updatedStrValue = varStr.replace(arg -> findVariableStringReplacement(arg, path, ctx));
            return Optional.of(ConfigValueFactory.fromAnyRef(updatedStrValue));
        } else {
            // only one magic variable, we can return any type
            val magicVar = varStr.first();
            return findVariableReplacement(magicVar, path, ctx);
        }
    }

    private Optional<ConfigValue> findVariableReplacement(@NonNull String variableName,
                                                          @NonNull String path,
                                                          @NonNull Context ctx) {
        return Optional.ofNullable(ctx.map.get(path))
            .flatMap(set -> getUpdatableValue(variableName, set))
            .filter(UpdatableConfigValue::hasBeenUpdated)
            .map(e -> e.updatedConfigValue);
    }

    private Optional<String> findVariableStringReplacement(@NonNull String variableName,
                                                           @NonNull String path,
                                                           @NonNull Context ctx) {
        return findVariableReplacement(variableName, path, ctx).map(Tsc4j::stringify);
    }

    private Optional<UpdatableConfigValue> getUpdatableValue(String arg, Set<UpdatableConfigValue> set) {
        return set.stream()
            .filter(e -> e.variable.equals(arg))
            .findFirst();
    }

    @Override
    protected void doClose() {
        log.debug("{} closing {} value provider(s).", this, providers.size());
        providers.forEach(Tsc4jImplUtils::close);
    }

    @Override
    public String getType() {
        return "values";
    }

    /**
     * Variable spec
     */
    @RequiredArgsConstructor
    @ToString(doNotUseGetters = true)
    private static class ValueSpec {
        /**
         * Value provider type
         */
        @NonNull
        final String valueProviderType;

        /**
         * Value provider name
         */
        @NonNull
        final String valueProviderName;

        /**
         * Value name
         */
        @NonNull
        final String configValueName;
    }

    /**
     * Updatable value.
     */
    @RequiredArgsConstructor
    @EqualsAndHashCode(doNotUseGetters = true)
    @ToString(of = {"configPath", "variable", "updatedConfigValue"}, doNotUseGetters = true)
    private static final class UpdatableConfigValue {
        /**
         * {@link Config} path.
         */
        @NonNull
        final String configPath;

        /**
         * Variable (if original string contains {@code %{type:name:/foo.bar}} this field contains {@code foo.bar}
         */
        @NonNull
        final String variable;

        /**
         * Original config value in which {@link #variable} was found
         */
        @NonNull
        final ConfigValue origConfigValue;

        /**
         * Updated config value
         */
        @EqualsAndHashCode.Exclude
        volatile ConfigValue updatedConfigValue;

        /**
         * Tells whether config value was updated.
         *
         * @return true/false
         */
        boolean hasBeenUpdated() {
            return updatedConfigValue != null;
        }

        /**
         * Sets updated config value.
         *
         * @param newConfigValue new config value.
         * @see #origConfigValue
         */
        void setUpdatedConfigValue(@NonNull ConfigValue newConfigValue) {
            if (newConfigValue.valueType() != ConfigValueType.NULL) {
                this.updatedConfigValue = newConfigValue;
            }
        }
    }

    /**
     * {@link ConfigValueProviderConfigTransformer} context.
     */
    @ToString
    static final class Context {
        private final Map<String, Set<UpdatableConfigValue>> map = new LinkedHashMap<>();

        /**
         * Registers updatable value.
         *
         * @param value updatable value
         */
        void registerUpdatableValue(@NonNull UpdatableConfigValue value) {
            map.computeIfAbsent(value.configPath, p -> new LinkedHashSet<>()).add(value);
        }

        /**
         * Returns all registered config paths.
         *
         * @return config paths
         */
        Set<String> paths() {
            return map.keySet();
        }

        /**
         * Returns all updatable values registered at specified config path
         *
         * @param path config path
         * @return updatable values registered at specified config paths
         */
        Set<UpdatableConfigValue> values(@NonNull String path) {
            return Optional.ofNullable(map.get(path))
                .map(Collections::unmodifiableSet)
                .orElseThrow(() -> new IllegalArgumentException("Unregistered config path: " + path));
        }
    }

    /**
     * Context used to fetch variables from {@link ConfigValueProvider} instances and update {@link
     * UpdatableConfigValue} instances.
     */
    @Slf4j
    private static class UpdateContext {
        private final Map<ConfigValueProvider, Map<String, List<UpdatableConfigValue>>> map = new LinkedHashMap<>();

        /**
         * Registers single variable fetch task.
         *
         * @param provider value provider
         * @param variable variable
         * @param uv       updatable value
         */
        void registerTask(@NonNull ConfigValueProvider provider,
                          @NonNull String variable,
                          @NonNull UpdatableConfigValue uv) {
            val taskMap = map.computeIfAbsent(provider, k -> new LinkedHashMap<>());
            val updatableValues = taskMap.computeIfAbsent(variable, k -> new ArrayList<>(1));
            updatableValues.add(uv);
        }

        /**
         * Creates one values fetch task per value provider.
         *
         * @return list of callables
         */
        private List<Callable<ConfigValueProvider>> createPerValueProviderFetchTasks() {
            return map.entrySet().stream()
                .map(this::createValueProviderTask)
                .collect(Collectors.toList());
        }

        /**
         * Creates single value provider fetch task.
         *
         * @param e map entry containing {@link ConfigValueProvider} map of variable to list of {@link
         *          UpdatableConfigValue} instances
         * @return callable containing fetch task
         */
        private Callable<ConfigValueProvider> createValueProviderTask(
            @NonNull Map.Entry<ConfigValueProvider, Map<String, List<UpdatableConfigValue>>> e) {
            val provider = e.getKey();
            val providerVars = e.getValue().keySet();
            val varMap = e.getValue();

            return () -> executeValueProviderFetch(provider, providerVars, varMap);
        }

        private ConfigValueProvider executeValueProviderFetch(@NonNull ConfigValueProvider provider,
                                                              @NonNull Set<String> providerVars,
                                                              @NonNull Map<String, List<UpdatableConfigValue>> varMap) {
            val resultMap = provider.get(providerVars);
            log.trace("{} config value provider {} returned: {}", this, provider, resultMap);
            if (resultMap == null) {
                log.warn("{} config value provider {} returned null result map.", this, provider);
                return provider;
            }

            resultMap.forEach((varName, updatedValue) -> {
                varMap
                    .getOrDefault(varName, Collections.emptyList())
                    .forEach(uv -> uv.setUpdatedConfigValue(updatedValue));
            });

            return provider;
        }
    }

    /**
     * Builder for {@link ConfigValueProviderConfigTransformer}.
     */
    @Accessors(chain = true)
    public static class Builder extends ConfigTransformerBuilder<Builder> {
        @NonNull
        @Getter
        @Setter
        private List<ConfigValueProvider> configValueProviders = new ArrayList<>();

        /**
         * Adds value providers.
         *
         * @param providers value providers
         * @return reference to itself
         */
        public Builder withProviders(@NonNull ConfigValueProvider... providers) {
            return withProviders(Arrays.asList(providers));
        }

        /**
         * Adds value providers.
         *
         * @param providers collection of value providers to add.
         * @return reference to itself
         */
        public Builder withProviders(@NonNull Collection<ConfigValueProvider> providers) {
            configValueProviders.addAll(providers);
            return getThis();
        }

        @Override
        protected Builder checkState() {
            if (configValueProviders.isEmpty()) {
                throw new IllegalStateException("No value providers were assigned.");
            }
            return super.checkState();
        }

        @Override
        public ConfigValueProviderConfigTransformer build() {
            return new ConfigValueProviderConfigTransformer(this);
        }
    }
}
