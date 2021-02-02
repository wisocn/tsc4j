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

package com.github.tsc4j.testsupport

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils
import spock.lang.Shared
import spock.lang.Specification

@Slf4j
abstract class BaseSpec extends Specification {
    /**
     * List of items to cleanup at the end of the test
     */
    @Shared
    List<String> cleanupList = []

    /**
     * Current temporary directory
     */
    String tmpDir = null

    def cleanupSpec() {
        cleanupList.each {
            def file = new File(it)
            if (!file.exists()) {
                return
            }
            if (file.isFile()) {
                log.debug("removing temporary file: {}", file)
                file.delete()
            } else if (file.isDirectory()) {
                log.debug("removing temporary directory: {}", file)
                FileUtils.deleteDirectory(file)
            }
        }
        cleanupList = []
    }

    /**
     * Creates new temporary directory.
     * @return new temporary directory as string
     */
    def newTmpDir() {
        def prefix = "." + getClass().getName().toLowerCase().replaceAll('[^\\w\\-\\.]+', '') + "."
        def systemTmpDir = System.getProperty("java.io.tmpdir") ?: "/tmp"
        def file = new File(systemTmpDir + "/" + prefix + System.nanoTime())
        if (!file.mkdir()) {
            throw new RuntimeException("Cannot create temporary directory: " + file)
        }

        cleanupList.add(file.toString())
        log.debug("created temporary directory: {}", file)
        file.toString()
    }

    /**
     * Gets current temporary directory.
     * @return temporary directory
     */
    def getTmpDir() {
        if (!tmpDir) {
            tmpDir = newTmpDir()
        }
        tmpDir
    }

    /**
     * Returns config file path as {@link java.io.File}.
     *
     * @param path config file path on classpath or filesystem.
     * @return file
     */
    File getConfigFile(String path) {
        path = path.replaceAll('^/+', '/')
        if (!path.startsWith('/')) {
            path = '/' + path
        }

        // check filesystem...
        def file = new File(path)
        if (file.exists() && file.isFile()) {
            log.trace("path {} exists on filesystem", path)
            return file
        }

        // look at classpath
        def url = getClass().getResource(path)
        if (url == null) {
            throw new IllegalArgumentException("Path doesn't exist on classpath or filesystem: " + path)
        }
        log.trace("path {} exists on classpath: {}", path, url)
        new File(url.getPath())
    }

    def copyFromClasspathToTmpDirRecursive(String classpathSrcDir) {
        def src = getConfigFile(classpathSrcDir)
        def tmpDir = getTmpDir()
        FileUtils.copyDirectory(src, new File(tmpDir))
        tmpDir
    }

    def copyFromClasspathToTmpdir(String srcFile) {
        copyFromClasspathToTmpdir(srcFile, srcFile)
    }

    def copyFromClasspathToTmpdir(String srcFile, String dstFile) {
        def src = getConfigFile(srcFile)
        def dst = new File(getTmpDir() + dstFile)
        FileUtils.copyFile(src, dst)
        dst
    }

    Config loadConfig(String path) {
        return loadConfig(getConfigFile(path))
    }

    Config loadConfig(File file) {
        ConfigFactory.parseFile(file)
    }
}
