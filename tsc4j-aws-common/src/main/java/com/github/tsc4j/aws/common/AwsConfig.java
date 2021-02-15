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

package com.github.tsc4j.aws.common;

import com.github.tsc4j.api.WithConfig;
import com.typesafe.config.Config;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.time.Duration;

/**
 * Class holding AWS client configuration.
 */
@Data
@Accessors(chain = true)
@ToString(doNotUseGetters = true)
@EqualsAndHashCode(doNotUseGetters = true, onlyExplicitlyIncluded = true)
public final class AwsConfig implements WithConfig {
    /**
     * Use AWS anonymous auth? This one takes precedence over {@link #getAccessKeyId()}/{@link #getSecretAccessKey()}.
     */
    private boolean anonymousAuth = false;

    /**
     * <p>AWS access key ({@code aws_access_key_id})</p>
     * <p>NOTE:both {@link #getAccessKeyId()} and {@link #getSecretAccessKey()} must be defined in order to override
     * default configuration from files/environment/machine profile</p>
     */
    private String accessKeyId;

    /**
     * <p>AWS secret key ({@code aws_secret_access_key})</p>
     * <p>NOTE:both {@link #getAccessKeyId()} and {@link #getSecretAccessKey()} must be defined in order to override
     * default configuration from files/environment/machine profile</p>
     */
    private String secretAccessKey;

    /**
     * AWS region; if undefined region is read from configuration/environment profile
     */
    private String region;

    /**
     * AWS API endpoint; useful only for local testing, otherwise you should just set region.
     *
     * @see #setRegion(String)
     * @see #getRegion()
     */
    private String endpoint;

    /**
     * Try to use gzip compression for aws requests? (default: true)
     */
    private boolean gzip = true;

    /**
     * AWS client execution timeout (default: 10 seconds)
     */
    private Duration timeout = Duration.ofSeconds(10);

    /**
     * How many connections to aws endpoint can client create? (default: 100)
     */
    private int maxConnections = 100;

    /**
     * How many retry attempts should client perform in case of aws service errors? (default: 0)
     */
    private int maxErrorRetry = 0;

    /**
     * S3 client path-style access.
     */
    private Boolean s3PathStyleAccess = null;

    @Override
    public void withConfig(@NonNull Config cfg) {
        cfgBoolean(cfg, "anonymous-auth", this::setAnonymousAuth);
        cfgBoolean(cfg, "anonymous-auth", this::setAnonymousAuth);
        cfgString(cfg, "access-key-id", this::setAccessKeyId);
        cfgString(cfg, "secret-access-key", this::setSecretAccessKey);
        cfgString(cfg, "region", this::setRegion);
        cfgString(cfg, "endpoint", this::setEndpoint);
        cfgBoolean(cfg, "gzip", this::setGzip);
        cfgDuration(cfg, "timeout", this::setTimeout);
        cfgInt(cfg, "max-connections", this::setMaxConnections);
        cfgInt(cfg, "max-error-retry", this::setMaxErrorRetry);
        cfgBoolean(cfg, "s3-path-style-access", this::setS3PathStyleAccess);
    }
}
