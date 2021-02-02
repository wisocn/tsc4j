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


import com.github.tsc4j.api.WithConfig;
import com.github.tsc4j.core.impl.ClasspathConfigSource;
import com.github.tsc4j.core.impl.CliConfigSource;
import com.github.tsc4j.core.impl.ConfigValueProviderConfigTransformer;
import com.github.tsc4j.core.impl.NoopConfigTransformer;
import com.github.tsc4j.core.impl.SimpleTsc4jCache;
import com.github.tsc4j.core.impl.Stopwatch;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Various utilities for tsc4j internal aliases.<p/>
 *
 * <b>WARNING:</b> your application code SHOULD NOT depend on any
 */
@Slf4j
@UtilityClass
public class Tsc4jImplUtils {
    private static final boolean IS_AOT = Boolean.getBoolean("com.oracle.graalvm.isaot");

    /**
     * Project name (value: <b>{@value}</b>)
     */
    public static final String NAME = "tsc4j";

    private static final Pattern INVALID_PATTERN = Pattern.compile("[^\\w\\-]+");

    public static final String PROP_PREFIX = NAME + ".";
    public static final String PROP_CONFIG = "config";
    public static final String PROP_APPNAME = "appname";
    public static final String PROP_DATACENTER = "datacenter";
    public static final String PROP_ZONE = "zone";
    public static final String PROP_ENVS = "envs";

    /**
     * System property name that forces specific bean mapper (value: <b>{@value}</b>)
     */
    public static final String PROP_BEAN_MAPPER = "bean-mapper";

    public static final List<String> PROP_NAMES = Collections.unmodifiableList(Arrays.asList(
        PROP_CONFIG, PROP_APPNAME, PROP_DATACENTER, PROP_ZONE, PROP_ENVS));

    /**
     * @see #getPropertyNameListIndex(String)
     */
    private static final Pattern PROPERTY_LIST_PATTERN = Pattern.compile("\\[(\\d+)\\]$");

    /**
     * tsc4j bootstrap config filenames on classpath.
     *
     * @see Tsc4jImplUtils#loadBootstrapConfigFromClasspath(String, Collection)
     * @see #BOOTSTRAP_CONFIG_FILES
     */
    private static final List<String> BOOTSTRAP_CONFIG_FILES = createTsc4jConfigFilenames();

    private static final long INIT_TIMESTAMP = System.currentTimeMillis();

    private static List<String> createTsc4jConfigFilenames() {
        val list = Stream.of(NAME, "reloadable-config")
            .map(e -> "/" + e)
            .flatMap(e -> Stream.of(e, e + "-${env}"))
            .map(e -> e + ".conf")
            .collect(Collectors.toList());
        return Collections.unmodifiableList(list);
    }

    private static Pattern STRING_TO_LIST_SPLIT_PATTERN = Pattern.compile("\\s*[;,]+\\s*");
    private static ThreadLocal<MessageDigest> DIGEST_TL =
        ThreadLocal.withInitial(Tsc4jImplUtils::createMessageDigest);
    private final Map<String, AtomicInteger> threadPoolCounters = new ConcurrentHashMap<>();

    /**
     * Default environment name (value:<b>{@value}</b>)
     */
    public static final String DEFAULT_ENV_NAME = "default";

    /**
     * Default datacenter name (value:<b>{@value}</b>)
     */
    public static final String DEFAULT_DATACENTER_NAME = "default";

    /**
     * Default timeout in seconds for {@link #parallelCall(Collection)} (value: <b>{@value}</b>)
     */
    protected static final long DEFAULT_TIMEOUT = 60;

    /**
     * {@link Config} key that specified whether this instance should be initialized (value: <b>{@value}</b>)<p/>
     *
     * <b>NOTE:</b> This value defaults to true, if omitted.
     *
     * @see #configuredInstance(Class, Config, int)
     */
    static final String KEY_ENABLED = "enabled";

    /**
     * {@link Config} key that specified whether this instance initialization is optional and that exceptions
     * during initialization should be treated as warnings, not errors (value: <b>{@value}</b>)<p/>
     *
     * <b>NOTE:</b> This value defaults to true, if omitted.
     *
     * @see #configuredInstance(Class, Config, int)
     */
    static final String KEY_OPTIONAL = "optional";

    /**
     * {@link Config} paths that are removed before configuring builder.
     *
     * @see #configuredInstance(Class, Config, int)
     */
    static final String KEY_IMPLEMENTATION = "impl";


    private final Object executorLock = new Object();

    /**
     * Default shared executor service
     */
    private volatile ExecutorService defaultExecutor;

    /**
     * Default shared scheduled executor service
     */
    private volatile ScheduledExecutorService scheduledExecutor;

    static final List<String> SPECIAL_KEYS = Collections.unmodifiableList(Arrays.asList(
        KEY_IMPLEMENTATION, KEY_ENABLED, KEY_OPTIONAL));

    @SneakyThrows
    private MessageDigest createMessageDigest() {
        return MessageDigest.getInstance("SHA-256");
    }

    private MessageDigest getMessageDigest() {
        val digest = DIGEST_TL.get();
        digest.reset();
        return digest;
    }

    /**
     * Computes checksum of specified object.
     *
     * @param object object to checksum
     * @return object checksum
     */
    public String objectChecksum(Object object) {
        if (object == null) {
            return objectChecksum("");
        }

        val base = object.hashCode() + "|" + object.toString();
        val hash = getMessageDigest().digest(base.getBytes(StandardCharsets.UTF_8));

        val hexString = new StringBuilder();
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public boolean objectChecksumDiffers(Object a, Object b) {
        val checksumA = objectChecksum(a);
        val checksumB = objectChecksum(b);
        return !checksumA.equals(checksumB);
    }

    /**
     * Constructs safe runnable that catches all exceptions during execution.
     *
     * @param runnable runnable
     * @return safe runnable
     */
    public Runnable safeRunnable(@NonNull Runnable runnable) {
        return () -> {
            try {
                runnable.run();
            } catch (Exception e) {
                log.error("exception while executing runnable: {}", e.getMessage(), e);
            }
        };
    }

    /**
     * Checks executor service for validity.
     *
     * @param service executor service
     * @param <T>     executor type
     * @return the same executor service
     * @throws NullPointerException     in case of null arguments
     * @throws IllegalArgumentException if executor is shutdown or terminated
     */
    public <T extends ExecutorService> T checkExecutor(@NonNull T service) {
        if (service.isShutdown() || service.isTerminated()) {
            throw new IllegalArgumentException("Cannot use shut down or terminated executor service: " + service);
        }
        return service;
    }

    /**
     * Shuts down executor and waits for termination.
     *
     * @param executor executor to shut down
     * @throws NullPointerException in case of null arguments
     * @throws InterruptedException if thread is being interrupted while waiting for shutdown
     */
    public void shutdownExecutor(ExecutorService executor) {
        shutdownExecutor(executor, 5, TimeUnit.SECONDS);
    }

    /**
     * Shuts down executor and waits for termination.
     *
     * @param executor executor to shut down
     * @param timeout  shutdown timeout before forcible shutdown
     * @param unit     shutdown timeunit
     * @throws NullPointerException in case of null arguments
     * @throws InterruptedException if thread is being interrupted while waiting for shutdown
     */
    @SneakyThrows
    public void shutdownExecutor(ExecutorService executor, long timeout, @NonNull TimeUnit unit) {
        if (executor == null) {
            return;
        }

        val sw = new Stopwatch();
        if (!executor.isShutdown()) {
            log.debug("shutting down executor: {}", executor);
            executor.shutdown();
        }

        if (!executor.isTerminated()) {
            if (!executor.awaitTermination(timeout, unit)) {
                val tasks = executor.shutdownNow();
                if (!tasks.isEmpty()) {
                    log.warn("{} tasks remained unexecuted after executor service was shut down: {}",
                        tasks.size(), tasks);
                }
            }
            log.debug("successfully shut down executor in {}: {}", sw, executor);
        }
    }

    /**
     * Closes object if it implements {@link Closeable}.
     *
     * @param obj closeable
     * @return true if closing was successful, otherwise false.
     */
    public static boolean close(Object obj) {
        if (!(obj instanceof Closeable)) {
            return false;
        }
        return close((Closeable) obj);
    }

    /**
     * Closes closeable.
     *
     * @param closeable closeable
     * @return true if closing was successful, otherwise false.
     */
    public static boolean close(Closeable closeable) {
        return close(closeable, log);
    }

    /**
     * Closes closeable.
     *
     * @param closeable closeable
     * @param log       logger used for logging in a case of closing failure
     * @return true if closing was successful, otherwise false.
     */
    public boolean close(Closeable closeable, @NonNull Logger log) {
        if (closeable != null) {
            try {
                closeable.close();
                return true;
            } catch (Exception e) {
                log.error("error closing {}: {}", closeable, e.getMessage(), e);
                return false;
            }
        }
        return false;
    }

    /**
     * Converts collection to stream of unique trimmed strings, with empty strings filtered out.
     *
     * @param c ollection
     * @return stream
     * @throws NullPointerException in case of null arguments
     */
    public Stream<String> uniqStream(@NonNull Collection<String> c) {
        return uniqStream(c.stream());
    }

    /**
     * Converts stream of strings of unique trimmed strings, with empty strings filtered out.
     *
     * @param stream of strings
     * @return unique stream of trimmed strings
     * @throws NullPointerException in case of null arguments
     */
    public Stream<String> uniqStream(@NonNull Stream<String> stream) {
        return stream
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(e -> !e.isEmpty())
            .distinct();
    }

    /**
     * Returns list containing unique trimmed strings, with empty strings filtered out.
     *
     * @param c collection
     * @return immutable list of unique trimmed strings, with empty strings filtered out.
     * @throws NullPointerException in case of null arguments
     * @see #uniqStream(Collection)
     */
    public List<String> toUniqueList(@NonNull Collection<String> c) {
        val list = uniqStream(c).collect(Collectors.toList());

        if (list.isEmpty()) {
            return Collections.emptyList();
        } else if (list.size() == 1) {
            return Collections.singletonList(list.iterator().next());
        } else {
            return Collections.unmodifiableList(list);
        }
    }

    /**
     * Tries to convert specified object to string list.
     *
     * @param o object, can be null
     * @return string list.
     */
    public List<String> toStringList(Object o) {
        if (o instanceof List) {
            val stringStream = ((List<?>) o).stream()
                .map(Object::toString)
                .map(Tsc4jImplUtils::toStringStream)
                .flatMap(stream -> stream);
            return uniqStream(stringStream).collect(Collectors.toList());
        } else if (o instanceof String) {
            return toStringList((String) o);
        }
        return Collections.emptyList();
    }

    /**
     * Converts comma/semicolon separated string to list of strings.
     *
     * @param str string to to split
     * @return list of strings.
     * @see #STRING_TO_LIST_SPLIT_PATTERN
     */
    public Stream<String> toStringStream(String str) {
        return (str == null) ?
            Stream.empty()
            :
            uniqStream(Stream.of(STRING_TO_LIST_SPLIT_PATTERN.split(str, 1000)));
    }

    /**
     * Converts comma/semicolon separated string to list of strings.
     *
     * @param str string to to split
     * @return list of strings.
     * @see #STRING_TO_LIST_SPLIT_PATTERN
     */
    public List<String> toStringList(String str) {
        return toStringStream(str).collect(Collectors.toList());
    }

    /**
     * Opens file for reading from filesystem and if it does not exists there tries to open it from classpath.
     *
     * @param filename filename
     * @return optional of input stream
     */
    public Optional<InputStream> openFromFilesystemOrClassPath(String filename) {
        return openFromFilesystem(filename)
            .map(Optional::of)
            .orElseGet(() -> openFromClassPath(filename));
    }

    /**
     * Opens file from filesystem.
     *
     * @param filename filename to open
     * @return optional of input stream
     * @throws IOException when file cannot be opened due to access restrictions or filesystem error
     */
    @SneakyThrows
    public Optional<InputStream> openFromFilesystem(@NonNull String filename) {
        val path = Paths.get(filename);
        if (!(Files.exists(path) && Files.isRegularFile(path))) {
            log.debug("file doesn't exist on filesystem: {}", path);
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(Files.newInputStream(path))
                .map(is -> {
                    log.debug("successfully opened file from filesystem: {}", path);
                    return is;
                });
        } catch (FileSystemException e) {
            throw e;
        } catch (IOException e) {
            log.warn("cannot open file {} from filesystem: {}", path, e.toString());
            return Optional.empty();
        }
    }

    /**
     * Opens file from classpath.
     *
     * @param filename filename
     * @return optional of input stream
     */
    public Optional<InputStream> openFromClassPath(@NonNull String filename) {
        val fname = ((!filename.startsWith("/")) ? "/" + filename : filename).replaceAll("^/+", "/");
        log.trace("trying to open from classpath: {}", fname);

        return Optional.ofNullable(Tsc4jImplUtils.class.getResourceAsStream(fname))
            .map(is -> {
                log.debug("successfully opened file from classpath: {}", fname);
                return is;
            });
    }

    /**
     * Sanitizes application name.
     *
     * @param appName application name
     * @return sanitized application name
     * @throws IllegalArgumentException if application name contains invalid characters
     */
    public String sanitizeAppName(@NonNull String appName) {
        return validateString(appName, "application name");
    }

    /**
     * Sanitizes application environment names.
     *
     * @param envs collection of application environment names
     * @return unmodifiable unique list of sanitized environment names
     * @throws IllegalArgumentException if any env name contains invalid characters
     */
    public List<String> sanitizeEnvs(@NonNull Collection<String> envs) {
        val list = Tsc4jImplUtils.uniqStream(envs)
            .map(e -> validateString(e, "environment name"))
            .collect(Collectors.toList());
        return list.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    /**
     * Loads configuration from {@code byte[]} source.
     *
     * @param configBytes configuration stored in byte array.
     * @param origin      config origin, name, path, see {@link com.typesafe.config.ConfigOrigin}
     * @return config instance.
     * @throws NullPointerException                in case of null arguments
     * @throws com.typesafe.config.ConfigException when configuration cannot be loaded or parsed from the source
     * @see Config#origin()
     */
    public Config readConfig(@NonNull byte[] configBytes, @NonNull String origin) {
        return readConfig(new ByteArrayInputStream(configBytes), origin);
    }

    /**
     * Loads configuration from {@link String} source.
     *
     * @param configString input stream to read config from
     * @param origin       config origin, name, path, see {@link com.typesafe.config.ConfigOrigin}
     * @return config instance.
     * @throws NullPointerException                in case of null arguments
     * @throws com.typesafe.config.ConfigException when configuration cannot be loaded or parsed from the source
     * @see Config#origin()
     */
    public Config readConfig(@NonNull String configString, @NonNull String origin) {
        return readConfig(configString.getBytes(UTF_8), origin);
    }

    /**
     * Loads configuration from {@link InputStream} source; {@code inputStream} is automatically closed after
     * configuration is parsed.
     *
     * @param inputStream input stream to read config from
     * @param origin      config origin, name, path, see {@link com.typesafe.config.ConfigOrigin}
     * @return config instance.
     * @throws NullPointerException                in case of null arguments
     * @throws com.typesafe.config.ConfigException when configuration cannot be loaded or parsed from the source
     * @see Config#origin()
     */
    public Config readConfig(@NonNull InputStream inputStream, @NonNull String origin) {
        return readConfig(new InputStreamReader(inputStream, UTF_8), origin);
    }

    /**
     * Loads configuration from {@link Reader} source; {@code reader} is automatically closed after
     * configuration is parsed.
     *
     * @param reader reader to read from
     * @param origin config origin, name, path, see {@link com.typesafe.config.ConfigOrigin}
     * @return config instance.
     * @throws NullPointerException                in case of null arguments
     * @throws com.typesafe.config.ConfigException when configuration cannot be loaded or parsed from the source
     * @see Config#origin()
     */
    public Config readConfig(@NonNull Reader reader, @NonNull String origin) {
        try {
            val parseOpt = ConfigParseOptions.defaults().setOriginDescription(origin);
            return ConfigFactory.parseReader(reader, parseOpt);
        } finally {
            close(reader, log);
        }
    }

    /**
     * Converts specified exception to runtime exception.
     *
     * @param t exception
     * @return t as runtime exception
     * @throws NullPointerException in case of null argumentd
     */
    public RuntimeException toRuntimeException(@NonNull Throwable t) {
        return (t instanceof RuntimeException) ? (RuntimeException) t : new RuntimeException(t.getMessage(), t);
    }

    /**
     * Invokes function if config key is present
     *
     * @param config    config object
     * @param path      config path
     * @param converter value converter
     * @param <E>       value type
     * @return optional of value.
     */
    public static <E> Optional<E> configVal(@NonNull Config config,
                                            @NonNull String path,
                                            @NonNull BiFunction<Config, String, E> converter) {
        val realPath = Tsc4j.configPath(path);
        return Stream.of(realPath, toCamelCase(realPath))
            .filter(p -> config.hasPath(p))
            .map(p -> converter.apply(config, p))
            .findFirst();
    }

    /**
     * Runs given {@code consumer} if given config {@path} exists.
     * <p>
     * Example usage:<br/>
     * {@code
     * configVal(config, "some.path", Config::getString, this::setSomePath);
     * }
     *
     * @param config    config instance to fetch value from
     * @param path      config path from which fetch value from
     * @param converter {@link Config} converter bi-function
     * @param consumer  consumer to run if value is present
     * @param <E>       type
     * @return optional of fetched value from config
     * @see #configVal(Config, String, BiFunction)
     */
    public static <E> Optional<E> configVal(@NonNull Config config,
                                            @NonNull String path,
                                            @NonNull BiFunction<Config, String, E> converter,
                                            @NonNull Consumer<E> consumer) {
        val opt = configVal(config, path, converter);
        opt.ifPresent(consumer::accept);
        return opt;
    }

    /**
     * Performs configuration scan invoking visitor on all found configuration paths.
     *
     * @param config  config to scan
     * @param visitor config visitor
     * @see #scanConfigValue(String, ConfigValue, BiConsumer)
     */
    public void scanConfig(@NonNull Config config, @NonNull BiConsumer<String, ConfigValue> visitor) {
        config.entrySet().stream()
            .sorted(Comparator.comparing(Map.Entry::getKey))
            .forEach(e -> visitor.accept(e.getKey(), e.getValue()));
    }

    /**
     * Performs configuration object scan invoking visitor on all elements (including object and list elements)
     * separately.
     *
     * @param object  config object to scan
     * @param visitor config value visitor
     * @see #scanConfig(Config, BiConsumer)
     * @see Config#root()
     */
    public void scanConfigObject(@NonNull ConfigObject object,
                                 @NonNull BiConsumer<String, ConfigValue> visitor) {
        scanConfigValue("", object, visitor);
    }

    /**
     * Performs configuration scan invoking visitor on all elements (including object and list elements) separately.
     *
     * @param path    config value path, may be empty
     * @param value   arbitrary config value
     * @param visitor config value visitor
     * @see #scanConfig(Config, BiConsumer)
     */
    private void scanConfigValue(@NonNull String path,
                                 @NonNull ConfigValue value,
                                 @NonNull BiConsumer<String, ConfigValue> visitor) {
        val type = value.valueType();
        if (type == ConfigValueType.LIST) {
            ((ConfigList) value).forEach(e -> scanConfigValue(path, e, visitor));
        } else if (type == ConfigValueType.OBJECT) {
            val obj = (ConfigObject) value;
            obj.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(e -> {
                    val realPath = (path.isEmpty()) ? e.getKey() : path + "." + e.getKey();
                    scanConfigValue(realPath, e.getValue(), visitor);
                });
        } else {
            visitor.accept(path, value);
        }
    }

    /**
     * Safely trims provided nullable string.
     *
     * @param s string to trim
     * @return trimmed string, empty string if argument was null
     */
    public static String validString(String s) {
        return optString(s).orElse("");
    }

    /**
     * Safely trims nullable string.
     *
     * @param s string
     * @return optional containing trimmed string
     */
    public static Optional<String> optString(String s) {
        return Optional.ofNullable(s)
            .map(String::trim)
            .filter(e -> !e.isEmpty());
    }

    /**
     * Returns default tsc4j executor service
     *
     * @return executor service
     */
    public ExecutorService defaultExecutor() {
        if (defaultExecutor == null) {
            synchronized (executorLock) {
                if (defaultExecutor == null) {
                    defaultExecutor = createExecutorService("default");
                    registerShutdownHook(() -> shutdownExecutor(defaultExecutor()));
                }
            }
        }

        return defaultExecutor;
    }

    public ScheduledExecutorService defaultScheduledExecutor() {
        if (scheduledExecutor == null) {
            synchronized (executorLock) {
                if (scheduledExecutor == null) {
                    scheduledExecutor = new ScheduledThreadPoolExecutor(1, createThreadFactory("rc"));
                    registerShutdownHook(() -> shutdownExecutor(scheduledExecutor));
                }
            }
        }

        return scheduledExecutor;
    }

    private void registerShutdownHook(@NonNull Runnable runnable) {
        Runtime.getRuntime().addShutdownHook(new Thread(runnable));
    }

    /**
     * Submits all callables to it and waits until all results are collected.
     * <p>
     * Default execution timeout is {@value #DEFAULT_TIMEOUT} seconds.
     *
     * @param callables callables to execute
     * @param <T>       callable return type
     * @return list of results
     * @throws java.util.concurrent.TimeoutException when timeout expires and all tasks didn't finish yet
     * @throws InterruptedException                  if thread waiting for results gets interrupted
     * @throws Exception                             if any of calls fails with exception, first exception cause will be
     *                                               thrown
     * @see #createExecutorService(String)
     * @see #sequentialCall(Collection)
     */
    private <T> List<T> parallelCall(@NonNull Collection<Callable<T>> callables) {
        return parallelCall(callables, DEFAULT_TIMEOUT, TimeUnit.SECONDS);
    }

    /**
     * Creates new temporary executor service, submits all callables to it and waits until all results are collected.
     *
     * @param callables callables to execute
     * @param timeout   execution timeout
     * @param unit      execution timeout unit
     * @param <T>       callable return type
     * @return list of results
     * @throws java.util.concurrent.TimeoutException when timeout expires and all tasks didn't finish yet
     * @throws InterruptedException                  if thread waiting for results gets interrupted
     * @throws Exception                             if any of calls fails with exception, first exception cause will be
     * @see #createExecutorService(String)
     */
    private <T> List<T> parallelCall(@NonNull Collection<Callable<T>> callables,
                                     long timeout,
                                     @NonNull TimeUnit unit) {
        return parallelCall(defaultExecutor(), callables, timeout, unit);
    }

    /**
     * Submits specified callables in newly created executor service, waits for tasks to complete and returns result in
     * a list with the same order that were specified in {@code callables}.<p/>
     * <b>WARNING:</b>This method is not intended to be called in a tight loop, because it creates and shuts down it's
     * own {@link ExecutorService}.
     *
     * @param callables callables to execute
     * @param <T>       result type
     * @return list of results
     * @throws java.util.concurrent.TimeoutException when timeout expires and all tasks didn't finish yet
     * @throws InterruptedException                  if thread waiting for results gets interrupted
     * @throws Exception                             if any of calls fails with exception, first exception cause will be
     *                                               thrown
     */
    private <T> List<T> parallelCall(@NonNull ExecutorService executor,
                                     @NonNull Collection<Callable<T>> callables,
                                     long timeout,
                                     @NonNull TimeUnit unit) {
        if (callables.isEmpty()) {
            return Collections.emptyList();
        }

        val futures = callables.stream()
            .map(executor::submit)
            .collect(Collectors.toList());
        return futures.stream()
            .map(f -> collectFutureResult(f, timeout, unit))
            .collect(Collectors.toList());
    }

    /**
     * Creates executor service used for quick async tasks.
     *
     * @param name executor service name
     * @return executor service
     */
    private ExecutorService createExecutorService(@NonNull String name) {
        val threadFactory = createThreadFactory(name);
        return new ThreadPoolExecutor(0, 500, 5, TimeUnit.SECONDS,
            new SynchronousQueue<>(), threadFactory);
    }

    @SneakyThrows
    private <T> T collectFutureResult(@NonNull Future<T> f, long timeout, @NonNull TimeUnit unit) {
        try {
            return f.get(timeout, unit);
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }

    /**
     * Stream friendly wrapper for invoking callables because it doesn't contain {@code throws Exception} in the
     * signature.
     *
     * @param callable callable to invoke
     * @param <T>      callable return type
     * @return callable result
     * @throws Exception if callable throws exception
     */
    @SneakyThrows
    public <T> T invokeCallable(Callable<T> callable) {
        return callable.call();
    }

    /**
     * Invokes all callables sequentially and returns their results as a list in the same order than in collection.
     *
     * @param callables collection of callables.
     * @param <T>       callable return type
     * @return list of results
     * @throws Exception if any callable throws exception
     * @see #parallelCall(Collection)
     */
    private <T> List<T> sequentialCall(@NonNull Collection<Callable<T>> callables) {
        return callables.stream()
            .map(Tsc4jImplUtils::invokeCallable)
            .collect(Collectors.toList());
    }

    /**
     * Invokes all callables in
     *
     * @param callables tasks to run
     * @param parallel  run tasks in parallel or not
     * @param <T>       callable return type
     * @return list of callable results
     * @throws Exception if any of tasks throw
     */
    public <T> List<T> runTasks(@NonNull Collection<Callable<T>> callables, boolean parallel) {
        // SubstrateVM, TLS and multithreading don't play along well
        // with aws-java-sdk; disable paralelism if running under substratevm
        if (isSubstrateVm()) {
            parallel = false;
        }

        return parallel ? parallelCall(callables) : sequentialCall(callables);
    }

    /**
     * Tells whether program is running in
     * <a href="https://github.com/oracle/graal/tree/master/substratevm">SubstrateVM</a>
     *
     * @return true/false
     */
    private boolean isSubstrateVm() {
        return IS_AOT;
    }

    /**
     * Creates tsc4j thread factory.
     *
     * @param name thread pool name
     * @return thread factory.
     */
    protected ThreadFactory createThreadFactory(@NonNull String name) {
        val poolNum = threadPoolCount(name);
        val threadCounter = new AtomicInteger();
        return runnable -> {
            val threadName = String.format("%s-%s-%d-%d", NAME, name, poolNum, threadCounter.incrementAndGet());
            val thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setName(threadName);
            return thread;
        };
    }

    private int threadPoolCount(@NonNull String name) {
        val counter = threadPoolCounters.computeIfAbsent(name, key -> new AtomicInteger());
        return counter.incrementAndGet();
    }

    /**
     * Converts snake-case string to camel-cased string.
     *
     * @param s string to camel case
     * @return camel-cased string
     */
    public String toCamelCase(@NonNull String s) {
        if (!(s.contains("-") || s.contains("_"))) {
            return s;
        }

        val chunks = Stream.of(s.split("\\s*[\\-_]+\\s*", 100))
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(e -> !e.isEmpty())
            .collect(Collectors.toList());

        if (chunks.isEmpty()) {
            return s;
        }

        val iterator = chunks.iterator();

        val sb = new StringBuilder(iterator.next().toLowerCase());
        while (iterator.hasNext()) {
            val e = iterator.next();
            val item = Character.toUpperCase(e.charAt(0)) + e.substring(1).toLowerCase();
            sb.append(item);
        }
        return sb.toString();
    }

    /**
     * Trims specified string, checks that it's not empty and checks whether it contains invalid characters.
     *
     * @param str         string to validate
     * @param description string name/description
     * @return trimmed string.
     * @throws NullPointerException     in case of null arguments
     * @throws IllegalArgumentException in case of invalid input string
     */
    public static String validateString(@NonNull String str, @NonNull String description) {
        str = str.trim();
        val error = "Invalid " + description + " '" + str + "': ";
        if (str.isEmpty()) {
            throw new IllegalArgumentException(error + "empty strings are not allowed.");
        }
        if (INVALID_PATTERN.matcher(str).find()) {
            throw new IllegalArgumentException(error + "string contains invalid characters.");
        }

        return str;
    }

    /**
     * Creates {@link ConfigTransformer} instance from specified config.<p/>
     * <p>
     * Configuration object can contain any settings recognized by {@link ConfigTransformerBuilder#withConfig(Config)}
     * in addition to the following ones:
     * <ul>
     * <li><b>impl</b> (string, default: none): transformer implementation name or fully qualified class name</li>
     * <li><b>enabled</b> (boolean, default: true): if false, this method will always return empty optional</li>
     * <li><b>name</b> (string, default: none): transformer name</li>
     * </ul>
     *
     * @param config config that contains config transformer configuration
     * @return optional of config transformer
     * @throws RuntimeException       if transformer cannot be loaded, configured or initialized
     * @throws ClassNotFoundException if implementation cannot be found on classpath
     */
    public Optional<ConfigTransformer> createTransformer(@NonNull Config config, int cfgNum) {
        return configuredInstance(ConfigTransformer.class, config, cfgNum);
    }

    /**
     * Creates aggregated config source from list of sources defined in tsc4j config.
     *
     * @param config                 tsc4j bootstrap config
     * @param appEnvs                application's enabled environments
     * @param overrideConfigSupplier override config supplier
     * @param fallbackConfigSupplier fallback config supplier
     * @return config source
     * @throws RuntimeException if any of defined config sources cannot be initialized
     * @see Tsc4jConfig#getSources()
     */
    public ConfigSource aggConfigSource(@NonNull Tsc4jConfig config,
                                        @NonNull Collection<String> appEnvs,
                                        @NonNull Supplier<Config> overrideConfigSupplier,
                                        @NonNull Supplier<Config> fallbackConfigSupplier) {
        val sources = createInstances(config.getSources(), appEnvs, Tsc4jImplUtils::createConfigSource);

        if (sources.isEmpty()) {
            sources.add(createDefaultClasspathConfigSource());
        }

        if (config.isCliEnabled()) {
            sources.add(CliConfigSource.instance());
        }

        val result = AggConfigSource.builder()
            .overrideSupplier(overrideConfigSupplier)
            .fallbackSupplier(fallbackConfigSupplier)
            .sources(sources)
            .build();

        log.info("created aggregated config source using {} source(s): {}", sources.size(), sources);
        return result;
    }

    /**
     * Creates {@link ConfigSource} instance from specified config.<p/>
     * <p>
     * Configuration object can contain any settings recognized by {@link ConfigTransformerBuilder#withConfig(Config)}
     * in addition to the following ones:
     * <ul>
     * <li><b>impl</b> (string, default: none): config source implementation name or alias</li>
     * <li><b>enabled</b> (boolean, default: true): if false, this method will always return empty optional</li>
     * <li><b>name</b> (string, default: none): transformer name</li>
     * <li><b>optional</b> (boolean, default: false) if true, any initialization errors will be only logged, instead of
     * throwing errors</li>
     * </ul>
     *
     * @param config config that contains source configuration
     * @param cfgNum configuration number, can't be {@code < 1}.
     * @return optional of config source
     * @throws RuntimeException if non-optional config source cannot be initialized
     */
    public Optional<ConfigSource> createConfigSource(@NonNull Config config, int cfgNum) {
        return configuredInstance(ConfigSource.class, config, cfgNum);
    }

    /**
     * Creates default config source if other config sources are not present in bootstrap config.
     *
     * @return config source
     */
    private static ConfigSource createDefaultClasspathConfigSource() {
        return ClasspathConfigSource.defaultBuilder().build();
    }

    /**
     * Returns implementation name from given config.
     *
     * @param config config
     * @return optional of implementation
     * @see #KEY_IMPLEMENTATION
     */
    private Optional<String> getImplementation(@NonNull Config config) {
        return configVal(config, KEY_IMPLEMENTATION, Config::getString)
            .flatMap(e -> optString(e));
    }

    /**
     * Tells whether this configuration is optional.
     *
     * @param config configuration
     * @return true/false
     * @see #KEY_OPTIONAL
     */
    private boolean isOptional(@NonNull Config config) {
        return configVal(config, KEY_OPTIONAL, Config::getBoolean).orElse(false);
    }

    /**
     * Tries to return instance of {@code clazz}, configured with given {@code config}.
     *
     * @param clazz  class instance
     * @param config configuration to configure instance with.
     * @param cfgNum configuration number (for logging purposes only).
     * @param <T>    instance type
     * @return optional of configured instance.
     * @throws RuntimeException if something goes wrong during instance initialization/configuration
     * @see #KEY_ENABLED
     * @see #KEY_IMPLEMENTATION
     * @see #KEY_OPTIONAL
     */
    protected <T> Optional<T> configuredInstance(@NonNull Class<T> clazz, @NonNull Config config, int cfgNum) {
        if (cfgNum < 1) {
            throw new IllegalArgumentException("Configuration number (cfgNum) can't be < 1.");
        }

        val implOpt = getImplementation(config);
        if (!implOpt.isPresent()) {
            log.debug("{} config #{} lacks {} key, cannot determine implementation: {}",
                clazz.getSimpleName(), cfgNum, KEY_IMPLEMENTATION, Tsc4j.render(config));
            return Optional.empty();
        }

        // is this optional config? if it is we're going to return empty optional
        // in case of exceptions, but if it's not we're going to throw exception if we didn't
        // initialized it
        val isOptional = isOptional(config);

        try {
            val instanceOpt = implOpt
                .flatMap(impl -> getLoader(clazz, impl))
                .map(loader -> createConfiguredInstanceFromLoader(loader, config, cfgNum));
            return checkInstancePresence(instanceOpt, isOptional, clazz, config, cfgNum);
        } catch (Tsc4jException e) {
            throw e;
        } catch (Exception e) {
            if (isOptional) {
                log.warn("error creating optional {} #{}: {}", clazz.getSimpleName(), cfgNum, e.getMessage(), e);
                return Optional.empty();
            } else {
                throw Tsc4jException.of("Error creating %s #%d: %%s", e, clazz.getSimpleName(), cfgNum);
            }
        }
    }


    @SuppressWarnings("unchecked")
    private static <T> T createConfiguredInstanceFromLoader(Tsc4jLoader<T> loader, Config config, int cfgNum) {
        // can this loader create final instance?
        val instanceOpt = loader.getInstance();
        if (instanceOpt.isPresent()) {
            val instance = instanceOpt.get();
            log.debug("loader {} returned concrete instance: {}", loader, instance);
            if (instance instanceof WithConfig) {
                val configuredInstance = ((WithConfig) instance).withConfig(config);
                log.debug("configured instance {} to {}", instance, configuredInstance);
                return (T) configuredInstance;
            } else {
                return instance;
            }
        }

        // looks like the only option we have is to get a builder from loader
        val builderOpt = loader.getBuilder();
        if (!builderOpt.isPresent()) {
            throw new IllegalArgumentException("Can't create instance of " + loader.forClass().getName() +
                ": can't create instance or builder.");
        }
        val builder = builderOpt.get();
        // configure builder with given config
        builder.withConfig(config);
        log.debug("configured builder {} with {}", builderOpt, config);

        // create instance
        return builder.build();
    }

    private static <T> Optional<T> checkInstancePresence(Optional<T> instanceOpt,
                                                         boolean isOptional,
                                                         Class<?> clazz,
                                                         Config config,
                                                         int cfgNum) {
        if (!instanceOpt.isPresent()) {
            if (isOptional) {
                log.warn("optional {} instance #{} was not created from config {}, tolerating.",
                    clazz.getSimpleName(), cfgNum, Tsc4j.render(config));
            } else {
                throw new IllegalStateException("Non optional " + clazz.getSimpleName() +
                    " instance #" + cfgNum + " wasn't instantiated from config: " + Tsc4j.render(config));
            }
        }

        return instanceOpt;
    }

    /**
     * Tells whether given source/value-provider/transformer configuration is valid
     *
     * @param config config instance containing configuration
     * @param envs   enabled environments
     * @return true/false
     */
    private boolean isEnabledConfig(@NonNull Config config, @NonNull Collection<String> envs) {
        val isEnabled = config.hasPath(KEY_ENABLED) ? config.getBoolean(KEY_ENABLED) : true;
        if (!isEnabled) {
            return false;
        }

        val ifAllEnvsEnabled = getStringList(config, "if-all-enabled-envs");
        if (!ifAllEnvsEnabled.isEmpty()) {
            return envs.containsAll(ifAllEnvsEnabled);
        }

        val ifAnyEnvEnabled = getStringList(config, "if-any-enabled-env");
        if (!ifAnyEnvEnabled.isEmpty()) {
            return ifAnyEnvEnabled.stream().anyMatch(envs::contains);
        }

        return true;
    }

    private List<String> getStringList(Config config, String path) {
        return config.hasPath(path) ? toUniqueList(config.getStringList(path)) : Collections.emptyList();
    }

    /**
     * Configures specified builder (if is instance of {@link WithConfig}) with specified config instance.
     *
     * @param builder builder
     * @param config  config
     * @return builder
     */
    protected <T extends AbstractBuilder> T configureBuilder(@NonNull T builder, @NonNull Config config) {
        val cfg = removeSpecialConfigKeys(config);
        log.debug("configuring builder instance {} with config: {}", builder, cfg);
        builder.withConfig(cfg);
        return builder;
    }

    /**
     * Creates aggregated config transformer.
     *
     * @param config  bootstrap config
     * @param appEnvs application's enabled environments
     * @return config transformer
     * @throws RuntimeException if any of transformers cannot be initialized
     * @see Tsc4jConfig#getTransformers()
     */
    public ConfigTransformer aggConfigTransformer(@NonNull Tsc4jConfig config, @NonNull Collection<String> appEnvs) {
        // initialize configured transformers
        val transformers = createInstances(config.getTransformers(), appEnvs, Tsc4jImplUtils::createTransformer);

        // try to create config transformer from config value providers
        createValueProviderTransformer(config, appEnvs).ifPresent(transformers::add);

        if (!transformers.isEmpty()) {
            log.info("initialized {} config transformer(s): {}", transformers.size(), transformers);
        }

        return (transformers.isEmpty()) ?
            NoopConfigTransformer.instance() : new AggConfigTransformer(transformers);
    }

    /**
     * Loads tsc4j configuration.
     *
     * @param configFile configuration filename on filesystem or classpath
     * @return tsc4j configuration
     * @throws IllegalArgumentException when file cannot be opened or parsed
     * @see Tsc4jConfig
     */
    public Tsc4jConfig loadBootstrapConfig(@NonNull String configFile) {
        return loadBootstrapConfigFromFile(configFile)
            .orElseThrow(() -> new IllegalArgumentException("Cannot open " + NAME + " config file: " + configFile));
    }

    /**
     * Loads tsc4j bootstrap configuration from classpath.
     *
     * @param loaderName loader name
     * @param envs       application running environments
     * @return tsc4j bootstrap config
     * @throws IllegalStateException when file cannot be opened or parsed
     * @see #loadBootstrapConfig(String)
     */
    public Tsc4jConfig loadBootstrapConfig(@NonNull String loaderName, @NonNull Collection<String> envs) {
        return loadBootstrapConfigFromClasspath(loaderName, envs)
            .orElseThrow(() -> new IllegalArgumentException(NAME + " bootstrap configuration file was not found."));
    }

    private static Optional<Tsc4jConfig> loadBootstrapConfigFromClasspath(@NonNull String loaderName,
                                                                          @NonNull Collection<String> envs) {
        envs = sanitizeEnvs(envs);
        val source = ClasspathConfigSource.builder()
            .setConfdEnabled(true)
            .setFailOnMissing(false)
            .setWarnOnMissing(false)
            .setVerbosePaths(false)
            .setPaths(BOOTSTRAP_CONFIG_FILES)
            .build();

        val query = ConfigQuery.builder()
            .appName(loaderName)
            .datacenter(DEFAULT_DATACENTER_NAME)
            .envs(envs)
            .availabilityZone(DEFAULT_DATACENTER_NAME)
            .build();

        val config = source.get(query);
        return Optional.ofNullable(Tsc4j.toBootstrapConfig(config));
    }

    /**
     * Splits given string using commas and semicolons to unique trimmed list.
     *
     * @param s string to split
     * @return list containing trimmed non-empty elements resulting from split
     */
    public List<String> splitToUniqueList(@NonNull String s) {
        s = s.trim();
        if (s.isEmpty()) {
            return Collections.emptyList();
        }
        val list = Arrays.asList(s.split("\\s*[,;]+\\s*"));
        return toUniqueList(list);
    }

    /**
     * Loads tsc4j configuration from filename which can be present on filesystem or classpath.
     *
     * @param filename tsc4j configuration filename
     * @return optional of tsc4j config
     */
    public Optional<Tsc4jConfig> loadBootstrapConfigFromFile(@NonNull String filename) {
        return openFromFilesystemOrClassPath(filename)
            .map(is -> readConfig(is, filename))
            .map(Tsc4j::toBootstrapConfig)
            .map(cfg -> {
                log.debug("loaded {} bootstrap config from file: {}", NAME, filename);
                return cfg;
            });
    }

    /**
     * Constructs tsc4j system property name.
     *
     * @param suffix property name suffix
     * @return tsc4j system property
     */
    public String tsc4jPropName(@NonNull String suffix) {
        return PROP_PREFIX + suffix;
    }

    /**
     * Translates system property name to environment variable name.
     *
     * @param propertyName property name
     * @return environment variable name
     */
    private String envVarName(@NonNull String propertyName) {
        return propertyName.replace('.', '_')
            .replace('-', '_')
            .toUpperCase();
    }

    /**
     * Returns tsc4j property value from environment variable or system properties (takes precedence over the former).
     *
     * @param propName property value
     * @return optional of sanitized property value
     * @see #tsc4jPropName(String)
     * @see #envVarName(String)
     */
    public Optional<String> tsc4jPropValue(@NonNull String propName) {
        return propValue(tsc4jPropName(propName));
    }

    /**
     * Retrieves property value from environment variable or system property.
     *
     * @param propName property name (if fetched from env var, dots are replaced with underscores and name is
     *                 upper-cased)
     * @return optional of sanitized fetched value from env var or system properties
     * @see #envVarName(String)
     */
    private Optional<String> propValue(@NonNull String propName) {
        return optString(System.getProperty(propName))
            .flatMap(e -> optString(e))
            .map(Optional::of)
            .orElseGet(() -> optString(System.getenv(envVarName(propName))));
    }

    /**
     * Tries to discover application name.
     *
     * @param appNameHint application name hint
     * @return application name
     * @throws IllegalArgumentException if application name cannot be detected or hint is invalid format.
     */
    public static String discoverAppName(String appNameHint) {
        return tsc4jPropValue(PROP_APPNAME)
            .map(Optional::of)
            .orElseGet(() -> optString(appNameHint))
            .map(s -> sanitizeAppName(s))
            .orElseThrow(() -> new IllegalArgumentException("Unable to discover application name."));
    }

    /**
     * Tries to discover datacenter.
     *
     * @param datacenterHint datacenter name hint
     * @return datacenter name
     */
    public String discoverDatacenterName(String datacenterHint) {
        val s = tsc4jPropValue(PROP_DATACENTER)
            .map(Optional::of)
            .orElseGet(() -> optString(datacenterHint))
            .flatMap(e -> optString(e))
            .orElse(DEFAULT_DATACENTER_NAME);

        return validateString(s, "datacenter name");
    }

    /**
     * Tries to discover running environment names.
     *
     * @param zoneHint availability zone name
     * @return availability zone name
     */
    public String discoverAvailabilityZone(String zoneHint) {
        val name = tsc4jPropValue(PROP_ZONE)
            .map(Optional::of)
            .orElseGet(() -> optString(zoneHint))
            .orElse(DEFAULT_ENV_NAME);
        return validateString(name, "availability zone name");
    }

    /**
     * Tries to discover running environment names.
     *
     * @param envsHint environment names hint
     * @return environment names
     */
    public List<String> discoverEnvNames(Collection<String> envsHint) {
        val x = tsc4jPropValue(PROP_ENVS)
            .map(e -> splitToUniqueList(e))
            .orElse(envsHint == null ? Collections.emptyList() : new ArrayList<>(envsHint));
        return sanitizeEnvs(x);
    }

    /**
     * Returns list of available config source loaders.
     *
     * @return list of loaders
     */
    public List<Tsc4jLoader<ConfigSource>> availableSources() {
        return getAvailableImplementations(ConfigSource.class);
    }

    /**
     * Returns list of available config transformer loaders.
     *
     * @return list of loaders
     */
    public List<Tsc4jLoader<ConfigTransformer>> availableTransformers() {
        return getAvailableImplementations(ConfigTransformer.class);
    }

    /**
     * Returns list of available config value provider loaders.
     *
     * @return list of loaders
     */
    public List<Tsc4jLoader<ConfigValueProvider>> availableValueProviders() {
        return getAvailableImplementations(ConfigValueProvider.class);
    }

    public <T> Optional<Tsc4jLoader<T>> getLoader(@NonNull Class<T> clazz, @NonNull String impl) {
        return getAvailableImplementations(clazz).stream()
            .filter(loader -> loader.supports(impl))
            .findFirst();
    }

    /**
     * Returns list of available {@link Tsc4jLoader} implementations that can construct given class.
     *
     * @param clazz class for which loader can create instances.
     * @param <T>   instance type
     * @return list of given implementations.
     */
    @SuppressWarnings("unchecked")
    private <T> List<Tsc4jLoader<T>> getAvailableImplementations(@NonNull Class<T> clazz) {
        return loadImplementations(Tsc4jLoader.class).stream()
            .filter(loader -> clazz.isAssignableFrom(loader.forClass()))
            .map(loader -> (Tsc4jLoader<T>) loader)
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * Loads implementations of given class using java service loader mechanism, catching errors.
     *
     * @param clazz interface class
     * @param <T>   interface type
     * @return list of initialized implementations
     * @throws ServiceConfigurationError if something goes wrong at initialization of {@link ServiceLoader}
     */
    public <T> List<T> loadImplementations(@NonNull Class<T> clazz) {
        val loader = ServiceLoader.load(clazz);
        val iterator = loader.iterator();

        val result = new ArrayList<T>();
        while (iterator.hasNext()) {
            fetchNextInstance(iterator, clazz).ifPresent(result::add);
        }

        return result;
    }

    private static <T> Optional<T> fetchNextInstance(Iterator<T> iterator, Class<T> clazz) {
        try {
            return Optional.ofNullable(iterator.next());
        } catch (Throwable t) {
            // YEP, ServiceLoader's Iterator throws `Error`, therefore we need to catch Throwable
            log.debug("error loading next service loader instance for: {}", clazz.getName(), t);
            return Optional.empty();
        }
    }

    /**
     * Removes special paths from configuration.
     *
     * @param config config instance
     * @return config instance with paths removed.
     * @see #SPECIAL_KEYS
     */
    protected Config removeSpecialConfigKeys(@NonNull Config config) {
        return Tsc4j.withoutPaths(config, SPECIAL_KEYS);
    }

    /**
     * Extracts property names from config paths. Paths that contain lists yield property names that end with {@code
     * [idx]}.
     *
     * @param config config object
     * @return set of property names
     */
    public Set<String> propertyNames(@NonNull Config config) {
        return propertyNames(config, true);
    }

    /**
     * Extracts property names from config paths. Paths that contain lists yield property names that end with {@code
     * [idx]}.
     *
     * @param config     config object
     * @param appendList also add property name, that is list without square brackets to the result?
     * @return set of property names
     */
    public Set<String> propertyNames(@NonNull Config config, boolean appendList) {
        val paths = config.entrySet().stream()
            .flatMap(e -> propertyNameStream(e, appendList))
            .sorted()
            .collect(Collectors.toCollection(LinkedHashSet::new));
        log.debug("config property names from current config: {}", paths);
        return Collections.unmodifiableSet(paths);
    }

    private Stream<String> propertyNameStream(Map.Entry<String, ConfigValue> e, boolean appendList) {
        val value = e.getValue();
        if (value.valueType() == ConfigValueType.LIST) {
            val size = ((List) value.unwrapped()).size();
            val resultStream = IntStream.range(0, size)
                .mapToObj(idx -> e.getKey() + "[" + idx + "]");

            return appendList ? Stream.concat(Stream.of(e.getKey()), resultStream) : resultStream;
        } else {
            return Stream.of(e.getKey());
        }
    }

    /**
     * Queries {@code config} instance for value of java-compatible property name {@code name}. Property name can
     * contain square brackets to identify list index if path resolves to array.
     *
     * @param name   property name
     * @param config config instance to query
     * @return property value if exists, otherwise {@code null}
     */
    public Object getPropertyFromConfig(@NonNull String name, @NonNull Config config) {
        val withoutDefaultValue = removeDefaultValueFromPropertyName(name);
        val configPath = getPathWithoutSquareBrackets(withoutDefaultValue);
        if (configPath.isEmpty()) {
            return null;
        }

        val tmpResult = (config.hasPath(configPath)) ? config.getAnyRef(configPath) : null;
        val result = doGetResult(name, tmpResult);

        log.trace("getProperty(name: '{}', path: '{}'): '{}'", name, configPath, result);
        return result;
    }

    private Object doGetResult(String name, Object result) {
        if (result == null) {
            return null;
        }

        if (result instanceof List) {
            val list = (List) result;
            val idx = getPropertyNameListIndex(name);
            if (idx < 0) {
                return list;
            }
            return (idx >= 0 && idx < list.size()) ? list.get(idx) : null;
        }

        return result;
    }

    /**
     * Removes potential default value from a property name. For example if {@code "foo.bar:defaultValue") is given
     * {@code "foo.bar"} will be returned.
     *
     * @param name spring property name
     * @return property name without default value
     */
    private String removeDefaultValueFromPropertyName(String name) {
        val idx = name.indexOf(':');
        return (idx < 0) ? name : name.substring(0, idx);
    }

    private String getPathWithoutSquareBrackets(String name) {
        val idx = name.indexOf('[');
        val realName = (idx < 0) ? name : name.substring(0, idx);
        return Tsc4j.configPath(realName);
    }

    private int getPropertyNameListIndex(String name) {
        val matcher = PROPERTY_LIST_PATTERN.matcher(name);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return -1;
    }

    /**
     * Partitions list into lists of sublists.
     *
     * @param source      source collection
     * @param maxElements sublist max elements
     * @param <T>         element type
     * @return list of original {@code source} list chunks that contain at most {@code maxElements} elements
     */
    public static <T> List<List<T>> partitionList(Collection<T> source, int maxElements) {
        if (maxElements < 1) {
            throw new IllegalArgumentException("maxElements cannot be < 1");
        }

        val realSource = (source instanceof List) ? (List<T>) source : new ArrayList<>(source);

        if (realSource.isEmpty()) {
            return Collections.emptyList();
        } else if (realSource.size() <= maxElements) {
            return Collections.singletonList(realSource);
        }

        // JDK8 doesn't provide takeUntil()/takeWhile() on Stream, this sucks, that's why
        // this operation uses old school java for loop.
        val result = new ArrayList<List<T>>();
        for (int fromIdx = 0; fromIdx < realSource.size(); fromIdx += maxElements) {
            int toIdx = fromIdx + maxElements;
            if (toIdx > realSource.size()) {
                toIdx = realSource.size();
            }
            val chunk = new ArrayList<T>(toIdx - fromIdx);
            chunk.addAll(realSource.subList(fromIdx, toIdx));
            result.add(chunk);
        }

        return result;
    }

    /**
     * Creates instances of given type.
     *
     * @param configs list of configurations, empty and null configurations will be filtered out
     * @param appEnvs application's enabled environments
     * @param creator creator function, takes configuration and it's sequence number, should create optional of
     *                created instance.
     * @param <T>     instance type
     * @return list of initialized instances
     * @throws NullPointerException in case of null arguments.
     * @throws RuntimeException     if any of non-optional instances cannot be created
     */
    private <T> List<T> createInstances(@NonNull Collection<Config> configs,
                                        @NonNull Collection<String> appEnvs,
                                        @NonNull BiFunction<Config, Integer, Optional<T>> creator) {
        val counter = new AtomicInteger();
        val result = configs.stream()
            .peek(it -> counter.incrementAndGet())
            .filter(Objects::nonNull)
            .filter(it -> isEnabledConfig(it, appEnvs))
            .map(cfg -> creator.apply(cfg, counter.incrementAndGet()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());

        if (log.isDebugEnabled()) {
            val types = result.stream()
                .map(e -> e.getClass().getSimpleName())
                .collect(Collectors.toList());
            log.debug("created {} {} instance(s): {} -> {}", result.size(), NAME, types, result);
        }

        return result;
    }

    /**
     * Creates value provider.
     *
     * @param config value provider configuration
     * @param cfgNum value provider configuration number (must be at least 1)
     * @return optional of value provider
     * @throws RuntimeException if value provider is non-optional and cannot be initialized.
     */
    public Optional<ConfigValueProvider> createValueProvider(@NonNull Config config, int cfgNum) {
        return configuredInstance(ConfigValueProvider.class, config, cfgNum);
    }

    private static Optional<ConfigTransformer> createValueProviderTransformer(@NonNull Tsc4jConfig config,
                                                                              @NonNull Collection<String> enabledEnvs) {
        return Optional.ofNullable(config.getValueProviders())
            .map(list -> createValueProviders(list, enabledEnvs))
            .filter(list -> !list.isEmpty())
            .map(Tsc4jImplUtils::createConfigValueTransformer);
    }

    /**
     * Creates value providers from given configs.
     *
     * @param configs collection of configs
     * @param appEnvs application's enabled environments
     * @return list of initialized value providers
     * @throws RuntimeException if any of non-optional value providers cannot be initialized
     */
    private static List<ConfigValueProvider> createValueProviders(@NonNull Collection<Config> configs,
                                                                  @NonNull Collection<String> appEnvs) {
        return createInstances(configs, appEnvs, Tsc4jImplUtils::createValueProvider);
    }

    private static ConfigTransformer createConfigValueTransformer(@NonNull Collection<ConfigValueProvider> configValueProviders) {
        val transformer = ConfigValueProviderConfigTransformer.builder()
            .withProviders(configValueProviders)
            .build();
        log.info("created config transformer from {} value provider(s): {}", configValueProviders.size(), configValueProviders);
        return transformer;
    }

    public static String timeSinceInitialization() {
        val ts = System.currentTimeMillis();
        return Stopwatch.toString(ts - INIT_TIMESTAMP);

    }

    private static volatile BeanMapper beanMapper;

    /**
     * Returns {@link BeanMapper} singleton.
     *
     * @return bean mapper
     * @see #PROP_BEAN_MAPPER
     */
    public static BeanMapper beanMapper() {
        BeanMapper mapper = beanMapper;
        if (mapper == null) {
            synchronized (Tsc4jImplUtils.class) {
                if ((mapper = beanMapper) == null) {
                    val type = tsc4jPropValue(PROP_BEAN_MAPPER).orElse("");
                    mapper = beanMapper = loadBeanMapper(type);
                    log.info("initialized {} bean mapper: {}", NAME, beanMapper);
                }
            }
        }
        return mapper;
    }

    /**
     * Creates most suitable {@link BeanMapper} instance.
     *
     * @return bean mapper
     * @throws RuntimeException if bean mapper can't be instantiated.
     */
    public static BeanMapper loadBeanMapper(String type) {
        val desiredType = optString(type).orElse("");
        val mappers = loadImplementations(BeanMapper.class);
        Collections.sort(mappers);
        log.debug("loaded {} bean mapper implementation(s): {}", mappers.size(), mappers);

        // do we need to create specific bean mapper type?
        if (!desiredType.isEmpty()) {
            return mappers.stream()
                .filter(e -> isValidBeanMapperType(desiredType, e))
                .findFirst()
                .orElseThrow(() ->
                    new IllegalArgumentException("Can't initialize " + NAME + " bean mapper: " + desiredType));
        }

        // first bean mapper will do :-)
        if (mappers.isEmpty()) {
            throw new IllegalStateException("Could not initialize any " + NAME + " bean mapper");
        }
        return mappers.get(0);
    }

    private static boolean isValidBeanMapperType(String desiredType, BeanMapper mapper) {
        val className = mapper.getClass().getName();
        return className.equals(desiredType) ||
            className.toLowerCase().equalsIgnoreCase(desiredType) ||
            className.endsWith("." + desiredType) ||
            className.toLowerCase().endsWith("." + desiredType.toLowerCase()) ||
            className.toLowerCase().endsWith("." + desiredType.toLowerCase() + "beanmapper");
    }

    /**
     * Creates new cache.
     *
     * @param name     cache name
     * @param cacheTtl cache entry TTL
     * @param <K>      key type
     * @param <E>      value type
     * @return new cache
     */
    public static <K, E> Tsc4jCache<K, E> newCache(@NonNull String name,
                                                   @NonNull Duration cacheTtl) {
        return newCache(name, cacheTtl, Clock.systemDefaultZone());
    }

    /**
     * Creates new cache.
     *
     * @param name     cache name
     * @param cacheTtl cache entry TTL
     * @param clock    clock
     * @param <K>      key type
     * @param <E>      value type
     * @return new cache
     */
    public static <K, E> Tsc4jCache<K, E> newCache(@NonNull String name,
                                                   @NonNull Duration cacheTtl,
                                                   @NonNull Clock clock) {
        return new SimpleTsc4jCache<>(name, cacheTtl, clock);
    }
}
