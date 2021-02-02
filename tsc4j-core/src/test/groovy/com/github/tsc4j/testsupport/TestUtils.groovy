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

import groovy.util.logging.Slf4j

import static com.github.tsc4j.core.Tsc4jImplUtils.PROP_NAMES
import static com.github.tsc4j.core.Tsc4jImplUtils.tsc4jPropName

@Slf4j
class TestUtils {
    private static final Map<String, String> envMap = unlockEnvMap()
    //static final Map<String, String> envMap = [:]

    static def tsc4jProps = PROP_NAMES.collect { tsc4jPropName(it) }
    static def tsc4jEnvVars = PROP_NAMES.collect { tsc4jPropName(it).toUpperCase().replace('.', '_') }

    static void clearTsc4jProps() {
        tsc4jProps.each { System.clearProperty(it) }
    }

    static void clearTsc4jEnvVars() {
        tsc4jEnvVars.each { envMap.remove(it) }
    }

    /**
     * Removes cleanup variables
     */
    static void cleanupVars() {
        clearTsc4jProps()
        clearTsc4jEnvVars()
    }

    static void debugSysProps() {
        def s = ""
        tsc4jProps.each { s += "  " + "".sprintf("%-20s: %s\n", it, System.getProperty(it)) }
        s = s.trim()
        log.info("tsc4j system properties:\n  {}", s)
    }

    static void debugEnvVars() {
        def s = ""
        tsc4jEnvVars.each { s += "  " + "".sprintf("%-20s: %s\n", it, System.getenv(it)) }
        s = s.trim()
        log.info("tsc4j env variables:\n  {}", s)
    }

    /**
     * Creates backup of system environment
     */
    static Map<String, String> envBackup() {
        new LinkedHashMap<String, String>(System.getenv())
    }

    /**
     * Restores system environment.
     * @param env map containing all environment variables
     */
    static void envRestore(Map<String, String> env) {
        envMap.clear()
        envMap.putAll(env)
    }

    /**
     * Sets environment variable.
     * @param name env var name
     * @param value env var value
     */
    static void setEnvVar(String name, String value) {
        envMap.put(name, value)
    }

    static Properties systemPropsBackup() {
        Properties props = new Properties()
        props.putAll(System.getProperties())
        props
    }

    static void systemPropsRestore(Properties props) {
        System.getProperties().clear()
        System.getProperties().putAll(props)
    }

    private static Map<String, String> unlockEnvMap() {
        // get env map field
        Class<?> clazz = Class.forName("java.lang.ProcessEnvironment")
        def envField = clazz.getDeclaredField("theUnmodifiableEnvironment")

        // make field non-final
        FieldHelper.makeNonFinal(envField)

        // make field accessible
        envField.setAccessible(true)

        // get the original env map, replace it with new, mutable one.
        Map<String, String> envMap = (Map<String, String>) envField.get(null)
        Map<String, String> mutableEnvMap = new LinkedHashMap<>(envMap)

        envField.set(null, mutableEnvMap)

        mutableEnvMap
    }
}
