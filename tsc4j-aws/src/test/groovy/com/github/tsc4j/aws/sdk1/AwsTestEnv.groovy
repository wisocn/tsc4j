/*
 * Copyright 2017 - 2019 tsc4j project
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
 *
 */

package com.github.tsc4j.aws.sdk1

import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.transfer.TransferManager
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClient
import com.amazonaws.services.simplesystemsmanagement.model.DeleteParametersRequest
import com.amazonaws.services.simplesystemsmanagement.model.ParameterType
import com.amazonaws.services.simplesystemsmanagement.model.PutParameterRequest
import com.github.tsc4j.core.Tsc4jImplUtils
import groovy.util.logging.Slf4j

@Slf4j
class AwsTestEnv {
    static def awsRegion = "us-east-1"
    static def awsEndpoint = "http://localhost:4566"

    static def ssmParameters = [
        "/a/x"  : [ParameterType.String, "a.x"],
        "/a/y"  : [ParameterType.StringList, "a.y,a,b,c", ["a.y", "a", "b", "c"]],
        "/a/z"  : [ParameterType.SecureString, "a.z"],
        "/b/c/d": [ParameterType.String, "42"]
    ]

    private static AWSSimpleSystemsManagementClient ssm
    private static AmazonS3 s3
    private static TransferManager tm

    static AWSSimpleSystemsManagementClient ssmClient() {
        if (ssm == null) {
            ssm = AWSSimpleSystemsManagementClient
                .builder()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(awsEndpoint, awsRegion))
                .build()
        }
        ssm
    }

    static AWSSimpleSystemsManagementClient setupSSM() {
        def ssm = ssmClient()
        cleanupSSM(ssm)
        createSSMParams(ssm)
    }

    static AWSSimpleSystemsManagementClient createSSMParams(AWSSimpleSystemsManagementClient ssm) {
        ssmParameters.each {
            def name = it.key
            def e = it.value
            def req = new PutParameterRequest()
                .withType(e[0])
                .withValue(e[1])
                .withName(name)
                .withDescription("desc_" + name)
                .withOverwrite(true)
            ssm.putParameter(req)
            log.info("created parameter: {} -> {}", name, req)
        }
        ssm
    }

    static AWSSimpleSystemsManagementClient cleanupSSM(AWSSimpleSystemsManagementClient ssm) {
        def facade = new SsmFacade(ssm, "foo", true, false)
        def names = facade.list().collect { it.getName() }

        Tsc4jImplUtils.partitionList(names, 10).collect {
            def req = new DeleteParametersRequest().withNames(it)
            ssm.deleteParameters(req)
            log.info("deleted {} parameters: {}", it.size(), it)
        }
        ssm
    }

    static AmazonS3 s3Client() {
        if (s3 == null) {
            def endpoint = new AwsClientBuilder.EndpointConfiguration(awsEndpoint, awsRegion)
            s3 = AmazonS3Client.builder()
                               .withEndpointConfiguration(endpoint)
                               .build()
        }
        s3
    }

    static TransferManager transferManager() {
        if (tm == null) {
            tm = new TransferManagerBuilder()
                .withDisableParallelDownloads(false)
                .withS3Client(s3Client())
                .withShutDownThreadPools(true)
                .build()
        }
        tm
    }

    static AmazonS3 uploadToS3(String srcDir, String bucketName, String bucketPrefix) {
        def tm = transferManager()
        def s3 = tm.getAmazonS3Client()

        def create = s3.createBucket(bucketName)
        log.info("bucket created: {}", create)

        log.info("uploading {} to s3://{}{}", srcDir, bucketName, "/")
        def res = tm.uploadDirectory(bucketName, "/", new File(srcDir), true)
        res.waitForCompletion()
        log.info("upload done: {}", srcDir)
        tm.getAmazonS3Client()
    }
}
