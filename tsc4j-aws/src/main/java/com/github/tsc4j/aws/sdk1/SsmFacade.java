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

package com.github.tsc4j.aws.sdk1;

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClient;
import com.amazonaws.services.simplesystemsmanagement.model.DescribeParametersRequest;
import com.amazonaws.services.simplesystemsmanagement.model.DescribeParametersResult;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathResult;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersResult;
import com.amazonaws.services.simplesystemsmanagement.model.Parameter;
import com.amazonaws.services.simplesystemsmanagement.model.ParameterMetadata;
import com.amazonaws.services.simplesystemsmanagement.model.ParameterType;
import com.github.tsc4j.aws.common.AwsConfig;
import com.github.tsc4j.core.BaseInstance;
import com.github.tsc4j.core.Tsc4jException;
import com.github.tsc4j.core.Tsc4jImplUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.Closeable;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.amazonaws.services.simplesystemsmanagement.model.ParameterType.StringList;

/**
 * Simple AWS SSM client facade.
 *
 * @see <a href="https://aws.amazon.com/systems-manager/">AWS Systems manager</a>
 */
@Slf4j
final class SsmFacade extends BaseInstance implements Closeable {
    /**
     * Maximum number of parameters in a single ssm request.
     *
     * @see <a href="https://docs.aws.amazon.com/systems-manager/latest/APIReference/API_GetParameters.html">AWS SSM
     *     Get parameters API Reference</a>
     */
    private static final int MAX_RESULTS = 10;

    /**
     * Max results requested in describe parameters request.
     *
     * @see <a href="https://docs.aws.amazon.com/systems-manager/latest/APIReference/API_DescribeParameters.html">AWS
     *     SSM Describe parameters API reference</a>
     */
    private static final int DESCRIBE_MAX_RESULTS = 50;

    /**
     * AWS SSM type.
     */
    static final String TYPE = "aws.ssm";

    private static final Pattern SPLIT_PATTERN = Pattern.compile("\\s*,\\s*");

    private final AWSSimpleSystemsManagement ssm;
    private final String id;
    private final boolean decrypt;
    private final boolean parallel;

    /**
     * Creates new instance.
     *
     * @param id       instance id
     * @param awsInfo  aws info
     * @param decrypt  decrypt secure parameters?
     * @param parallel parallel parameter fetching?
     */
    SsmFacade(@NonNull String id, @NonNull AwsConfig awsInfo, boolean decrypt, boolean parallel) {
        this(createSsmClient(awsInfo), id, decrypt, parallel);
    }

    /**
     * Creates new instance.
     *
     * @param ssm      ssm client
     * @param id       instance id
     * @param decrypt  decrypt secure parameters?
     * @param parallel parallel parameter fetching?
     */
    SsmFacade(@NonNull AWSSimpleSystemsManagement ssm, @NonNull String id, boolean decrypt, boolean parallel) {
        super(id);
        this.ssm = ssm;
        this.id = id;
        this.decrypt = decrypt;
        this.parallel = parallel;
    }

    /**
     * Creates AWS ssm client
     *
     * @param awsInfo aws info instance
     * @return aws ssm client
     */
    private static AWSSimpleSystemsManagement createSsmClient(@NonNull AwsConfig awsInfo) {
        return AwsSdk1Utils.configuredClient(AWSSimpleSystemsManagementClient::builder, awsInfo);
    }

    /**
     * Lists all AWS SSM parameters.
     *
     * @return list of discovered parameters.
     */
    List<ParameterMetadata> list() {
        return list(new DescribeParametersRequest());
    }

    /**
     * Lists all AWS SSM parameters that satisfy specified parameters request.
     *
     * @param request describe parameters request.
     * @return list of found parameters.
     */
    List<ParameterMetadata> list(@NonNull DescribeParametersRequest request) {
        request.withMaxResults(DESCRIBE_MAX_RESULTS);
        val result = new ArrayList<ParameterMetadata>();

        String nextToken = null;
        do {
            val response = describeParameters(ssm, request, nextToken);
            log.debug("{} received description of {} parameter(s).", this, response.getParameters().size());
            result.addAll(response.getParameters());
            nextToken = response.getNextToken();
        } while (nextToken != null && !nextToken.isEmpty());

        return result;
    }

    private DescribeParametersResult describeParameters(@NonNull AWSSimpleSystemsManagement ssm,
                                                        @NonNull DescribeParametersRequest request,
                                                        String token) {
        val realReq = request.clone();
        if (token != null) {
            realReq.withNextToken(token);
        }

        log.debug("{} describing aws ssm parameter store parameters: {}", this, realReq);
        try {
            return ssm.describeParameters(realReq);
        } catch (Exception e) {
            throw Tsc4jException.of("Error while describing AWS SSM parameters request %s: %%s",
                e, request.toString());
        }
    }

    /**
     * Converts parameter to config value.
     *
     * @param param parameter
     * @return config value
     */
    static ConfigValue toConfigValue(@NonNull Parameter param) {
        val type = ParameterType.fromValue(param.getType());

        log.trace("converting to ConfigValue: {}", param);
        val updated = Optional.ofNullable(param.getLastModifiedDate())
            .map(e -> e.toInstant().atZone(ZoneOffset.UTC).toString())
            .orElse("n/a");
        val originDescription = String.format("%s:%s, version: %d, modified: %s, arn: %s",
            TYPE, param.getName(), param.getVersion(), updated, param.getARN());

        if (type == StringList) {
            val chunks = SPLIT_PATTERN.split(param.getValue());
            val list = Arrays.asList(chunks);
            return ConfigValueFactory.fromIterable(list, originDescription);
        }
        return ConfigValueFactory.fromAnyRef(param.getValue(), originDescription);
    }

    /**
     * Converts collection of parameters to config.
     *
     * @param params collection of parameters
     * @return config.
     */
    Config toConfig(@NonNull Collection<Parameter> params) {
        return params.stream()
            .reduce(ConfigFactory.empty(),
                (cfg, param) -> cfg.withValue(parameterTooConfigPath(param), parameterToConfigValue(param)),
                (previous, current) -> current);
    }

    private ConfigValue parameterToConfigValue(@NonNull Parameter parameter) {
        return toConfigValue(parameter);
    }

    private String parameterTooConfigPath(@NonNull Parameter parameter) {
        return parameter.getName()
            .replace('/', '.')
            .replaceAll("^\\.*", "");
    }

    /**
     * Fetches parameters by one or more paths.
     *
     * @param paths parameters paths
     * @return list of fetched parameters.
     */
    List<Parameter> fetchByPath(String... paths) {
        return fetchByPath(Arrays.asList(paths));
    }

    /**
     * Fetches parameters by one or more paths.
     *
     * @param paths parameters paths
     * @return list of fetched parameters.
     */
    List<Parameter> fetchByPath(@NonNull Collection<String> paths) {
        val tasks = Tsc4jImplUtils.uniqStream(paths)
            .map(this::createGetParametersRequest)
            .map(this::toGetByPathTask)
            .collect(Collectors.toList());

        return runTasks(tasks, this.parallel).stream()
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
    }

    private GetParametersByPathRequest createGetParametersRequest(String path) {
        return new GetParametersByPathRequest()
            .withPath(path)
            .withMaxResults(MAX_RESULTS)
            .withRecursive(true)
            .withWithDecryption(decrypt);
    }

    private Callable<List<Parameter>> toGetByPathTask(@NonNull GetParametersByPathRequest req) {
        return () -> {
            try {
                return getParametersByPath(req);
            } catch (Exception e) {
                throw Tsc4jException.of("Error fetching AWS SSM parameters by path %s: %%s", e, req.getPath());
            }
        };
    }

    private List<Parameter> getParametersByPath(GetParametersByPathRequest req) {
        val result = new ArrayList<Parameter>();

        String nextToken = null;
        do {
            val response = getParametersByPath(req, nextToken);
            log.debug("{} fetched {} parameter(s) for path: {}", this, response.getParameters().size(), req.getPath());
            response.getParameters().stream()
                .sorted(Comparator.comparing(Parameter::getName))
                .forEach(result::add);
            nextToken = response.getNextToken();
        } while (nextToken != null && !nextToken.isEmpty());

        return result;

    }

    private GetParametersByPathResult getParametersByPath(@NonNull GetParametersByPathRequest req, String nextToken) {
        val realReq = req.clone().withNextToken(nextToken);
        return ssm.getParametersByPath(realReq);
    }

    /**
     * Fetches parameters.
     *
     * @param names parameter names
     * @return list of fetched parameters
     */
    List<Parameter> fetch(@NonNull List<String> names) {
        val requests = Tsc4jImplUtils.partitionList(names, MAX_RESULTS).stream()
            .map(e -> toGetParametersRequest(e, decrypt))
            .collect(Collectors.toList());

        val tasks = createFetchParametersTasks(ssm, requests);

        log.debug("{} created {} fetching tasks.", this, tasks.size());
        val results = runTasks(tasks, this.parallel);
        log.debug("{} retrieved {} get parameters results.", this, results.size());

        val invalidParams = results.stream()
            .flatMap(e -> e.getInvalidParameters().stream())
            .collect(Collectors.toList());
        if (!invalidParams.isEmpty()) {
            log.warn("{} invalid AWS SSM parameters: {}", this, invalidParams);
        }

        val params = results.stream()
            .flatMap(e -> e.getParameters().stream())
            .collect(Collectors.toList());

        log.debug("{} retrieved {} AWS SSM parameters.", this, params.size());
        log.trace("{} retrieved AWS SSM parameters: {}", this, params);

        return params;
    }

    private List<Callable<GetParametersResult>> createFetchParametersTasks(
        @NonNull AWSSimpleSystemsManagement ssm,
        @NonNull Collection<GetParametersRequest> requests) {
        return requests.stream()
            .map(request -> createParameterFetchTask(ssm, request))
            .collect(Collectors.toList());
    }

    private Callable<GetParametersResult> createParameterFetchTask(@NonNull AWSSimpleSystemsManagement ssm,
                                                                   @NonNull GetParametersRequest request) {
        return () -> {
            try {
                return ssm.getParameters(request);
            } catch (Exception e) {
                throw Tsc4jException.of("Error fetching %d AWS SSM parameters: %%s", e, request.getNames().size());
            }
        };
    }

    private static GetParametersRequest toGetParametersRequest(@NonNull List<String> names, boolean decrypt) {
        if (names.isEmpty()) {
            throw new IllegalArgumentException("Parameter names cannot be empty.");
        }
        if (names.size() > MAX_RESULTS) {
            throw new IllegalArgumentException("Parameter names length cannot be > " + MAX_RESULTS);
        }

        return new GetParametersRequest()
            .withNames(names)
            .withWithDecryption(decrypt);
    }

    @Override
    protected void doClose() {
        super.doClose();
        log.debug("{} closing ssm client: {}", this, ssm);
        ssm.shutdown();
    }

    @Override
    public String getType() {
        return id;
    }

    @Override
    public String toString() {
        return id;
    }
}
