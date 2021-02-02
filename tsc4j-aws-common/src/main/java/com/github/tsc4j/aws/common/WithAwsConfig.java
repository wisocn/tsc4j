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

import java.time.Duration;

/**
 * Poor man's mixin for adding {@link AwsConfig} to a class that implements this interface.
 *
 * @param <T> type of the actual implementing class.
 * @see AwsConfig
 */
@SuppressWarnings("unchecked")
public interface WithAwsConfig<T> {
    /**
     * Returns AWS info instance.
     *
     * @return aws info instance
     */
    AwsConfig getAwsConfig();

    default T setAnonymousAuth(boolean flag) {
        getAwsConfig().setAnonymousAuth(flag);
        return (T) this;
    }

    default T setAccessKeyId(String accessKey) {
        getAwsConfig().setAccessKeyId(accessKey);
        return (T) this;
    }

    default T setSecretAccessKey(String secretKey) {
        getAwsConfig().setSecretAccessKey(secretKey);
        return (T) this;
    }

    default T setRegion(String region) {
        getAwsConfig().setRegion(region);
        return (T) this;
    }

    default T setEndpoint(String awsEndpoint) {
        getAwsConfig().setEndpoint(awsEndpoint);
        return (T) this;
    }

    default T setGzip(boolean flag) {
        getAwsConfig().setGzip(flag);
        return (T) this;
    }

    default T setTimeout(Duration duration) {
        getAwsConfig().setTimeout(duration);
        return (T) this;
    }

    default T setMaxConnections(int maxConn) {
        getAwsConfig().setMaxConnections(maxConn);
        return (T) this;
    }

    default T setMaxErrorRetry(int maxErr) {
        getAwsConfig().setMaxErrorRetry(maxErr);
        return (T) this;
    }

    default T setS3PathStyleAccess(boolean flag) {
        getAwsConfig().setS3PathStyleAccess(flag);
        return (T) this;
    }
}
