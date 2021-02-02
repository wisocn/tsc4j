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

import com.github.tsc4j.api.Tsc4jBeanBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigUtil;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Various tsc4j supporting utilities.
 */
@Slf4j
@UtilityClass
public class Tsc4j {
    private static final ConfigRenderOptions RENDER_OPTS_CONCISE = ConfigRenderOptions.concise();
    private static final ConfigRenderOptions RENDER_OPTS_PRETTY = RENDER_OPTS_CONCISE.setFormatted(true);
    private static final ConfigRenderOptions RENDER_OPTS_WITH_COMMENTS = RENDER_OPTS_PRETTY.setComments(true);
    private static final ConfigRenderOptions RENDER_OPTS_VERBOSE = RENDER_OPTS_WITH_COMMENTS.setOriginComments(true);

    /**
     * Git properties file classpath location.
     *
     * @see #TSC4J_PROPERTIES
     */
    private static final String GIT_PROPERTIES_FILE = Tsc4j.class.getPackage().getName()
        .replace('.', '/') + "/git.properties";

    /**
     * tsc4j properties
     *
     * @see #GIT_PROPERTIES_FILE
     */
    private static final Properties TSC4J_PROPERTIES = loadTsc4jVersionProperties();

    /**
     * Pattern that matches if configuration path should be quoted.
     *
     * @see #sanitizePath(String)
     */
    private static final Pattern CFG_PATH_SHOULD_BE_QUOTED = Pattern.compile("[^\\w\\-\\.]");
    private static final Pattern PATH_SANITIZE_PATT_LEADING_DOTS = Pattern.compile("^\\.+");
    private static final Pattern PATH_SANITIZE_PATT_TRAILING_DOTS = Pattern.compile("\\.+$");
    private static final Pattern PATH_SANITIZE_PATT_MULTIPLE_DOTS = Pattern.compile("\\.{2,}");
    /**
     * Characters that cannot be part of config path.
     *
     * @see #sanitizePath(String)
     * @see <a href="https://github.com/lightbend/config/blob/master/config/src/main/java/com/typesafe/config/impl/Tokenizer.java#L302">Lightbend
     *     config Tokenizer.java on GitHub</a>
     */
    private static final String PATH_RESERVED_CHAR_STR = Pattern.quote("$\"{}[]:=,+#`^?!@*&\\");
    private static final Pattern PATH_RESERVED_CHARS_PATT = Pattern.compile("[" + PATH_RESERVED_CHAR_STR + "]+");

    /**
     * Renders configuration object to concise JSON string.
     *
     * @param config configuration object
     * @return config as JSON string
     */
    public String render(@NonNull Config config) {
        return render(config, 0);
    }

    /**
     * Renders configuration object to JSON string.
     *
     * @param config configuration object
     * @param pretty render pretty, human readable json?
     * @return config as JSON string
     */
    public String render(@NonNull Config config, boolean pretty) {
        return render(config, pretty ? 1 : 0);
    }

    /**
     * Renders given subpath on config instance.
     *
     * @param config    config instance to render.
     * @param verbosity verbosity level (0 - concise, 1 - pretty, 2 - pretty with comments, 3 - pretty with comments and
     *                  origin description)
     * @return rendered config value
     */
    public String render(@NonNull Config config, int verbosity) {
        return render(config, "", verbosity);
    }

    /**
     * Renders given config sub-path with given verbosity.
     *
     * @param config    config instance to render.
     * @param path      optional subpath to render, should be empty string to render entire config.
     * @param verbosity verbosity level (0 - concise, 1 - pretty, 2 - pretty with comments, 3 - pretty with comments and
     *                  origin description)
     * @return rendered config value
     * @throws IllegalArgumentException if {@code path} is non-empty and does not exist in given config.
     */
    public String render(@NonNull Config config, @NonNull String path, int verbosity) {
        val renderOpts = renderOptions(verbosity);

        if (!(path.isEmpty() || config.hasPathOrNull(path))) {
            val numPaths = config.root().size();
            val msg = String.format("Config (%d paths) doesn't contain path: '%s'", numPaths, path);
            throw new IllegalArgumentException(msg);
        }

        val fixedPath = configPath(path);
        val value = fixedPath.isEmpty() ? config.root() : config.getValue(fixedPath);
        return value.render(renderOpts);
    }

    /**
     * Returns render options based on verbosity.
     *
     * @param verbosity verbosity level
     * @return config render options
     */
    public ConfigRenderOptions renderOptions(int verbosity) {
        if (verbosity == 1) {
            return RENDER_OPTS_PRETTY;
        } else if (verbosity == 2) {
            return RENDER_OPTS_WITH_COMMENTS;
        } else if (verbosity >= 3) {
            return RENDER_OPTS_VERBOSE;
        }

        return RENDER_OPTS_CONCISE;
    }

    /**
     * Instantiates bean from {@link Config} instance. This method is similar to
     * {@link com.typesafe.config.ConfigBeanFactory#create(Config, Class)} but it behaves in a much more lenient way -
     * not all bean properties need to be present in {@link Config} object, setters don't need to have corresponding
     * getters, setters can be fluent, even immutable classes are supported trough builder pattern
     * (note that they must be annotated with {@link Tsc4jBeanBuilder}) annotation.
     *
     * @param config config
     * @param clazz  toBean class (can be normal, mutable toBean, or immutable, builder supported class - in this case
     *               it
     *               must be annotated with {@link Tsc4jBeanBuilder} annotation)
     * @param <T>    bean class type
     * @return bean instance
     * @throws NullPointerException                in case of null arguments
     * @throws com.typesafe.config.ConfigException in case of bad config/bean specification
     * @throws RuntimeException                    when something really weird happens
     * @see Tsc4jBeanBuilder if you're using lombok {@code @Value/@Builder}
     *     generated classes, you should add @TypeSafeBeanBuilder annotation
     */
    public <T> T toBean(@NonNull Config config, @NonNull Class<T> clazz) {
        return Tsc4jImplUtils.beanMapper().create(clazz, config, "");
    }

    /**
     * Sanitizes configuration path.
     *
     * @param path configuration path, can be null if you want to refer top-level element.
     * @return validated and sanitized configuration path that is safe to use with {@link Config#hasPath(String)}, if
     *     it's not empty.
     * @see Config#hasPath(String)
     * @see Config#atPath(String)
     * @see Config#withoutPath(String)
     * @see Config#withOnlyPath(String)
     */
    public String configPath(String path) {
        return Optional.ofNullable(path)
            .map(Tsc4j::sanitizePath)
            .orElse("");
    }

    private String sanitizePath(@NonNull String path) {
        // oh god, this is so ugly
        // first, remove all lightbend config path reserved characters
        String sanitized = PATH_RESERVED_CHARS_PATT.matcher(path).replaceAll("");

        // remove all double+ dots
        sanitized = PATH_SANITIZE_PATT_MULTIPLE_DOTS.matcher(sanitized).replaceAll("");

        // trim path
        sanitized = sanitized.trim();

        // remove any leading/trailing dots
        sanitized = PATH_SANITIZE_PATT_LEADING_DOTS.matcher(sanitized).replaceAll("");
        sanitized = PATH_SANITIZE_PATT_TRAILING_DOTS.matcher(sanitized).replaceAll("");

        sanitized = sanitized.trim();

        // config path `.` is not allowed as well
        if (sanitized.equals(".")) {
            return "";
        }

        // does the path need to be quoted?
        val doQuote = CFG_PATH_SHOULD_BE_QUOTED.matcher(sanitized).find();
        return doQuote ? ConfigUtil.quoteString(sanitized) : sanitized;
    }

    /**
     * Removes paths from given config instance.
     *
     * @param config config instance
     * @param paths  paths to remove
     * @return new config instance with removed system properties.
     */
    public Config withoutPaths(@NonNull Config config, @NonNull String... paths) {
        return withoutPaths(config, Arrays.asList(paths));
    }

    /**
     * Removes paths from given config instance.
     *
     * @param config config instance
     * @param paths  paths to remove
     * @return new config instance with removed system properties.
     */
    public Config withoutPaths(@NonNull Config config, @NonNull Collection<String> paths) {
        // Config instance throws exception if we're querying keys/paths with reserved/invalid chars in it,
        // sanitize paths to remove
        val pathsToRemove = paths.stream()
            .filter(Objects::nonNull)
            .map(Tsc4j::configPath)
            .filter(e -> !e.isEmpty())
            .collect(Collectors.toSet());

        // step 1: remove paths request to be removed
        val res = pathsToRemove.stream()
            .reduce(config, Config::withoutPath, (previous, current) -> current);

        // step 2: config.withoutPath removes key, but potentially leaves empty parent config object
        //         if requested path was the only value in that object.
        return interpolatePaths(pathsToRemove)
            .filter(e -> res.hasPath(e) && isEmptyConfigObject(res.getValue(e)))
            .reduce(res, Config::withoutPath, (previous, current) -> current);
    }

    /**
     * Splits given collection config paths.
     *
     * @param paths config paths
     * @return streams of interpolated config paths
     * @see #interpolatePath(String)
     */
    private static Stream<String> interpolatePaths(Collection<String> paths) {
        if (paths.isEmpty()) {
            return Stream.empty();
        }
        return paths.stream()
            .flatMap(Tsc4j::interpolatePath)
            .distinct()
            .sorted(Comparator.reverseOrder());
    }

    /**
     * Interpolates config path to it's chunks of subpaths. Result for path {@code x.y.z} is: {@code [ x, x.y, x.y.z]}
     *
     * @param path config path
     * @return config path chunks
     * @see #interpolatePaths(Collection)
     */
    private static Stream<String> interpolatePath(String path) {
        val chunks = ConfigUtil.splitPath(path);
        if (chunks.isEmpty()) {
            return Stream.empty();
        } else {
            return IntStream.range(0, chunks.size())
                .mapToObj(idx -> ConfigUtil.joinPath(chunks.subList(0, idx + 1)));
        }
    }

    private static boolean isEmptyConfigObject(@NonNull ConfigValue value) {
        if (value.valueType() == ConfigValueType.OBJECT) {
            val obj = (ConfigObject) value;

            // if object is empty, it's result is obviously true
            if (obj.isEmpty()) {
                return true;
            }

            // object is also empty if it contains only empty subobjects
            return obj.keySet().stream()
                .map(e -> isEmptyConfigObject(obj.get(e)))
                .reduce(true, (previous, current) -> previous && current);
        }
        return false;
    }

    /**
     * Removes system properties from given config instance.
     *
     * @param config config instance
     * @return new config instance with removed system properties.
     * @see #withoutPaths(Config, Collection)
     */
    public Config withoutSystemProperties(@NonNull Config config) {
        val propNames = System.getProperties().keySet()
            .stream()
            .map(Object::toString)
            .collect(Collectors.toList());
        return withoutPaths(config, propNames);
    }

    /**
     * Removes environment variables from given config instance.
     *
     * @param config config instance
     * @return new config instance with removed environment variables
     * @see #withoutPaths(Config, Collection)
     */
    public Config withoutEnvVars(@NonNull Config config) {
        return withoutPaths(config, System.getenv().keySet());
    }

    /**
     * Removes system properties and environment variables from given config instance.
     *
     * @param config config instance
     * @return new config instance without system properties and environment variables.
     */
    public Config withoutSystemPropertiesAndEnvVars(@NonNull Config config) {
        return withoutEnvVars(withoutSystemProperties(config));
    }

    /**
     * Returns tsc4j version.
     *
     * @return tsc4j version string
     * @see #versionProperties()
     */
    public String version() {
        return versionProperties().getProperty("git.build.version", "unknown-version");
    }

    /**
     * Returns full version properties.
     *
     * @return properties containing version and build information
     */
    public Properties versionProperties() {
        return (Properties) TSC4J_PROPERTIES.clone();
    }

    private static Properties loadTsc4jVersionProperties() {
        return Tsc4jImplUtils.openFromClassPath(GIT_PROPERTIES_FILE)
            .map(Tsc4j::loadProperties)
            .orElseGet(Properties::new);
    }

    /**
     * Converts normal {@link Config} instance to tsc4j bootstrap config.
     *
     * @param config config object
     * @return tsc4j config instance
     */
    public Tsc4jConfig toBootstrapConfig(@NonNull Config config) {
        log.debug("converting {} bootstrap config (resolved: {}): {}", Tsc4jImplUtils.NAME, config.isResolved(), config);
        try {
            val cfg = Tsc4j.resolveConfig(config);
            val bootstrapConfig = Tsc4jConfig.builder().withConfig(cfg).build();
            log.debug("created {} bootstrap config: {}", Tsc4jImplUtils.NAME, bootstrapConfig);
            return bootstrapConfig;
        } catch (Exception e) {
            throw Tsc4jException.of("Error creating %s bootstrap config: %%s", e, Tsc4jImplUtils.NAME);
        }
    }

    /**
     * Tries to resolve configuration by appending system properties and env variables to it if necessary.
     *
     * @param config config to resolve
     * @return resolved config
     * @throws com.typesafe.config.ConfigException when config cannot be resolved
     * @see ConfigFactory#defaultOverrides()
     * @see ConfigFactory#systemEnvironment()
     */
    public Config resolveConfig(@NonNull Config config) {
        if (config.isResolved()) {
            return config;
        }
        // try to resolve config, might fail with exception.
        val resolved = ConfigFactory.defaultOverrides()
            .withFallback(ConfigFactory.systemEnvironment())
            .withFallback(config)
            .resolve();

        // once resolved, system props and env vars are no longer needed
        return withoutSystemPropertiesAndEnvVars(resolved);
    }

    /**
     * Creates config source that encapsulates all sources and transformers defined in config.
     *
     * @param config tsc4j config
     * @return config source
     * @throws RuntimeException if any of sources or transformers cannot be initialized
     * @see #configSource(Tsc4jConfig, Collection, Supplier, Supplier)
     */
    public ConfigSource configSource(@NonNull Tsc4jConfig config, @NonNull Collection<String> appEnvs) {
        return configSource(config, appEnvs, ConfigFactory::empty, ConfigFactory::empty);
    }

    /**
     * Creates config source that encapsulates all sources and transformers defined in config.
     *
     * @param config                 tsc4j config
     * @param appEnvs                application's enabled environments
     * @param overrideConfigSupplier override config supplier
     * @param fallbackConfigSupplier fallback config supplier
     * @return config source
     * @throws RuntimeException if any of sources or transformers cannot be initialized
     */
    public ConfigSource configSource(@NonNull Tsc4jConfig config,
                                     @NonNull Collection<String> appEnvs,
                                     @NonNull Supplier<Config> overrideConfigSupplier,
                                     @NonNull Supplier<Config> fallbackConfigSupplier) {
        val source = Tsc4jImplUtils.aggConfigSource(config, appEnvs, overrideConfigSupplier, fallbackConfigSupplier);
        val transformer = Tsc4jImplUtils.aggConfigTransformer(config, appEnvs);
        val result = new ConfigSourceWithTransformer(source, transformer);
        log.debug("created config source: {}", result);
        return result;
    }

    @SneakyThrows
    private Properties loadProperties(@NonNull InputStream is) {
        try (val reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            val props = new Properties();
            props.load(reader);
            return props;
        }
    }

    /**
     * Stringifies config value.
     *
     * @param value config value
     * @return stringified version
     */
    public static String stringify(@NonNull ConfigValue value) {
        val type = value.valueType();
        if (type == ConfigValueType.STRING) {
            return value.unwrapped().toString();
        } else if (type == ConfigValueType.OBJECT) {
            return value.render(RENDER_OPTS_CONCISE);
        } else if (type == ConfigValueType.LIST) {
            return ((ConfigList) value).stream()
                .map(Tsc4j::stringify)
                .collect(Collectors.joining(","));
        } else {
            return value.render(RENDER_OPTS_CONCISE);
        }
    }
}
