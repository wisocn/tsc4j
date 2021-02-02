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

import com.github.tsc4j.core.Tsc4j;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * {@link String} holder that contains magic variables in a form of {@code %{foo.bar}}.
 */
@Slf4j
@ToString//(of = "str")
final class VarStr {
    // TODO: make % variable prefix MANDATORY
    private static final Pattern pattern = Pattern.compile("(?<!\\$)%?\\{([^\\}]+)\\}");

    private final String str;
    private final List<String> vars;

    /**
     * Creates new instance.
     *
     * @param str string containing magic variables
     */
    public VarStr(@NonNull String str) {
        this.vars = getVars(str);
        this.str = str;
    }

    /**
     * Returns number of magic variables found in enclosed string.
     *
     * @return number of magic variables
     */
    public int count() {
        return vars.size();
    }

    /**
     * Tells whether this instance contains at least one magic variable.
     *
     * @return true/false
     */
    public boolean isPresent() {
        return !isEmpty();
    }

    /**
     * Tells whether this instance contains no magic variables.
     *
     * @return true/false
     */
    public boolean isEmpty() {
        return vars.isEmpty();
    }

    /**
     * Returns first magic variable.
     *
     * @return first magic variable
     * @throws IllegalStateException if instance doesn't contain any magic variables.
     */
    public String first() {
        if (isEmpty()) {
            throw new IllegalStateException("Instance doesn't contain any magic variables.");
        }
        return vars.get(0).trim();
    }

    /**
     * Runs specified consumer for each variable value found in enclosed string.
     *
     * @param consumer consumer to run
     * @return reference to itself
     * @throws NullPointerException in case of null arguments
     * @throws RuntimeException     if consumer throws when invoked
     */
    public VarStr scan(@NonNull Consumer<String> consumer) {
        vars.forEach(e -> consumer.accept(e.trim()));
        return this;
    }

    /**
     * Scans enclosed string for variables and asks {@code mapper} function to provide replacement values.
     *
     * @param mapper mapper function; invoked with variable value, expected to return replacement value optional; empty
     *               optional should be returned by function if no replace operation should be done.
     * @return rewritten string
     * @throws NullPointerException in case of null arguments
     * @throws RuntimeException     if {@code mapper} function throws or returns {@code null}.
     */
    public String replace(@NonNull Function<String, Optional<String>> mapper) {
        if (isEmpty()) {
            return str;
        }

        val sb = new StringBuilder(str);

        vars.forEach(e -> mapper.apply(e.trim()).ifPresent(replacement -> {
            val search = formatVar(e);
            val result = sb.toString().replace(search, replacement);
            sb.delete(0, sb.length());
            sb.append(result);
        }));
        return sb.toString();
    }

    /**
     * Resolves var string's variables into {@link ConfigValue}.
     *
     * @param mapper mapping function that takes variable and returns optional of it's value.
     * @return config value
     * @throws RuntimeException if mapping function throws.
     */
    public ConfigValue resolve(@NonNull Function<String, Optional<ConfigValue>> mapper) {
        if (isEmpty()) {
            return ConfigValueFactory.fromAnyRef(str);
        }

        // if there's only one magic variable in varstr instance we can afford to return any ConfigValue type.
        if (vars.size() == 1) {
            val firstMagicVar = vars.get(0);
            val variable = formatVar(firstMagicVar);
            if (variable.equals(str.trim())) {
                return mapper.apply(firstMagicVar.trim())
                    .orElse(ConfigValueFactory.fromAnyRef(str));
            }
        }

        // otherwise we're going to always return String ConfigValue type
        val sb = new StringBuilder(str);
        vars.forEach(e -> mapper.apply(e.trim()).ifPresent(configValue -> {
            val search = formatVar(e);
            val replacement = Tsc4j.stringify(configValue);
            // log.trace("replacing '{}' with '{}'", search, replacement);
            val result = sb.toString().replace(search, replacement);
            sb.delete(0, sb.length());
            sb.append(result);
        }));
        return ConfigValueFactory.fromAnyRef(sb.toString());
    }

    private String formatVar(@NonNull String varName) {
        return "%{" + varName + "}";
    }

    private void doScan(@NonNull String str, @NonNull Consumer<String> consumer) {
        val matcher = pattern.matcher(str);

        int fromIdx = 0;
        while (matcher.find(fromIdx)) {
            val found = matcher.group(1);
            // TODO: change the following to +1 when `%` var prefix is defined as required.
            //fromIdx = matcher.start() + 2;
            fromIdx = matcher.end();

            if (!found.trim().isEmpty()) {
                consumer.accept(found);
            }
        }
    }

    private List<String> getVars(@NonNull String str) {
        val result = new ArrayList<String>();
        doScan(str, result::add);
        return result.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(result);
    }
}
