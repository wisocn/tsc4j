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

package com.github.tsc4j.api;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigUtil;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@UtilityClass
class ApiUtils {
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
     * config Tokenizer.java on GitHub</a>
     */
    private static final String PATH_RESERVED_CHAR_STR = Pattern.quote("$\"{}[]:=,+#`^?!@*&\\");
    private static final Pattern PATH_RESERVED_CHARS_PATT = Pattern.compile("[" + PATH_RESERVED_CHAR_STR + "]+");

    private static final Pattern CAMEL_CASE_SPLITTER = Pattern.compile("\\s*[\\-_]+\\s*");


    /**
     * Invokes function if config key is present
     *
     * @param config    config object
     * @param path      config path
     * @param converter value converter
     * @param <E>       value type
     * @return optional of value.
     */
    <E> Optional<E> cfgExtract(@NonNull Config config,
                               @NonNull String path,
                               @NonNull BiFunction<Config, String, E> converter) {
        val realPath = configPath(path);
        return Stream.of(realPath, toCamelCase(realPath))
            .filter(p -> config.hasPath(p))
            .map(p -> converter.apply(config, p))
            .findFirst();
    }


    String configPath(String path) {
        return Optional.ofNullable(path)
            .map(ApiUtils::sanitizePath)
            .orElse("");
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

        val chunks = Stream.of(CAMEL_CASE_SPLITTER.split(s, 100))
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

    static <T> T optionalIfPresent(@NonNull T value, @NonNull Consumer<T> consumer) {
        consumer.accept(value);
        return value;
    }
}
