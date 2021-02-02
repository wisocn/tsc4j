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

import com.github.tsc4j.aws.common.AwsCliCommand;
import com.github.tsc4j.core.Tsc4jImplUtils;
import com.jessecoyle.CredentialVersion;
import com.jessecoyle.JCredStash;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;
import picocli.CommandLine.PicocliException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * CLI command that allows credstash calls from java
 */
@Slf4j
@ToString
@CommandLine.Command(description = "Credstash test command.", sortOptions = false)
public final class CredstashCommand extends AwsCliCommand {
    private static final String KEY_FMT = "%-50.50s";
    private static final String VERSION_FMT = "%-9.9s";

    private static final String LIST_FMT = KEY_FMT + " " + VERSION_FMT + "\n";
    private static final String DISPLAY_FMT = KEY_FMT + " %s\n";

    @CommandLine.Option(names = {"-t", "--table"}, description = "credstash dynamodb table name (default: ${DEFAULT-VALUE})")
    private String tableName = CredstashConfigValueProvider.DEFAULT_TABLE_NAME;

    @CommandLine.Option(names = {"-L", "--list"}, description = "Lists credstash credentials")
    private boolean listCredentials = false;

    @CommandLine.Option(names = {"-A", "--all"}, description = "Fetch all credstash secrets")
    private boolean fetchAll = false;

    /**
     * Command line parameters.
     */
    @CommandLine.Parameters(description = "fetch specified credentials")
    private List<String> credentials = new ArrayList<>();

    @Override
    protected int doCall() {
//        val credstash = CredstashValueProvider.createCredstash(tableName, getAwsConfig());
        val credstashSupplier = CredstashConfigValueProvider.createCredstashSupplier(tableName, getAwsConfig());

        if (listCredentials) {
            return listSecrets(credstashSupplier.get());
        }

        if (!fetchAll && credentials.isEmpty()) {
            throw new PicocliException("No credential names to fetch were specified. Run with --help for instructions.");
        }

        return fetchSecrets(credstashSupplier.get(), credentials);
    }

    @Override
    public String getName() {
        return "credstash";
    }

    @SneakyThrows
    private int fetchSecrets(@NonNull JCredStash credstash, @NonNull Collection<String> credentials) {
        val names = fetchAll ?
            getSecrets(credstash, true).stream().map(e -> e.getName()).collect(Collectors.toList())
            :
            credentials;

        log.debug("fetching {} credstash credentials: {}", names.size(), names);
        val kmsCtx = Collections.<String, String>emptyMap();
        val credentialMap = new TreeMap<String, String>();
        val callables = Tsc4jImplUtils.toUniqueList(names).stream()
            .map(name -> createFetchCredentialCallable(credstash, name, kmsCtx, credentialMap))
            .collect(Collectors.toList());

        // run all calls in parallel.
        val results = runTasks(callables, true);
        log.debug("parallelCall() returned {} results.", results.size());

        if (!credentialMap.isEmpty()) {
            getStderr().printf(DISPLAY_FMT, "credential", "secret");
            credentialMap.forEach((name, secret) -> getStdout().printf(DISPLAY_FMT, name, secret));
        }

        return 0;
    }

    private Callable<String> createFetchCredentialCallable(@NonNull JCredStash credstash,
                                                           @NonNull String credentialName,
                                                           @NonNull Map<String, String> kmsCtx,
                                                           @NonNull Map<String, String> credentialMap) {
        return () -> {
            val secret = fetchCredential(credstash, credentialName, kmsCtx);
            credentialMap.put(credentialName, secret);
            return secret;
        };
    }

    private String fetchCredential(@NonNull JCredStash credstash,
                                   @NonNull String credentialName,
                                   @NonNull Map<String, String> kmsCtx) {
        val ts = System.currentTimeMillis();
        val secret = credstash.getSecret(credentialName, kmsCtx);
        val duration = System.currentTimeMillis() - ts;
        log.debug("fetched credstash secret {} in {} msec.", credentialName, duration);
        return secret.trim();
    }

    private int listSecrets(@NonNull JCredStash credstash) {
        log.debug("listing credstash credentials.");
        val ts = System.currentTimeMillis();
        val secrets = getSecrets(credstash, true);
        val duration = System.currentTimeMillis() - ts;
        log.debug("retrieved {} credstash credentials in {} msec.", secrets.size(), duration);
        getStderr().printf(LIST_FMT, "credential", "version");
        secrets.forEach(e -> getStdout().printf(LIST_FMT, e.getName(), cleanupVersion(e.getVersion())));
        return 0;
    }

    private static String cleanupVersion(@NonNull String version) {
        return version.replaceAll("^0*", "");
    }

    private List<CredentialVersion> getSecrets(@NonNull JCredStash credstash, boolean onlyLastVersion) {
        val secrets = credstash.listSecrets();
        return onlyLastVersion ? latestSecretVersions(secrets) : secrets;
    }

    private List<CredentialVersion> latestSecretVersions(@NonNull Collection<CredentialVersion> creds) {
        val map = new HashMap<String, CredentialVersion>();
        creds.forEach(e -> {
            val name = e.getName();
            val value = map.get(name);
            if (value == null) {
                map.put(name, e);
            } else {
                val curVersion = Long.parseLong(cleanupVersion(e.getVersion()));
                val prevVersion = Long.parseLong(cleanupVersion(value.getVersion()));
                if (curVersion > prevVersion) {
                    map.put(name, e);
                }
            }
        });

        return map.entrySet().stream()
            .sorted(Comparator.comparing(Map.Entry::getKey))
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());
    }

    @Override
    public String getGroup() {
        return "misc";
    }
}
