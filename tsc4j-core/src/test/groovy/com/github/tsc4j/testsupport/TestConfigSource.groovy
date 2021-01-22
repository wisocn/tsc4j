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

import com.github.tsc4j.core.ConfigQuery
import com.github.tsc4j.core.ConfigSource
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import java.util.function.Supplier

/**
 * {@link com.typesafe.config.Config} source class.
 */
class TestConfigSource {
    private static final Map<String, Object> defaultConfigMap = [
        "foo"       : "bar",

        "reloadable": [
            "first" : "foo",
            "second": "bar",
            "number": 15000,
            "third" : ["a", "b", "b"]
        ],

        "test"      : [
            "bean": [
                "aBoolean"  : true,
                "aInt"      : 42,
                "aLong"     : 667,
                "aDouble"   : 42.667,
                "aString"   : "foo",
                "extString" : "bar",

                "entryList" : [
                    ["x": "x1", "y": 10],
                    ["x": "x1", "y": 10],
                    ["x": "x1", "y": 10],
                ],

                "entrySet"  : [
                    ["x": "x1", "y": 10],
                    ["x": "x1", "y": 10],
                    ["x": "x1", "y": 10],
                ],

                "strMap"    : [
                    "a": "b",
                    "c": "d"
                ],

                "intBoolMap": [
                    "10": true,
                    "20": false
                ]
            ]
        ],
        "lists"     : [
            "booleans": [true, false, true],
            "ints"    : [42, 42, 42],
            "longs"   : [42, 42, 42],
            "doubles" : [42.42, 42.42, 42.42],
            "strings" : ["foo", "bar", "baz"],
            "beans"   : [
                [
                    "aBoolean": true,
                    "aInt"    : 42,
                    "aLong"   : 42,
                    "aDouble" : 42.42,
                    "aString" : "foo"
                ],
                [
                    "aBoolean": true,
                    "aInt"    : 42,
                    "aLong"   : 42,
                    "aDouble" : 42.42,
                    "aString" : "bar"
                ],
                [
                    "aBoolean": true,
                    "aInt"    : 42,
                    "aLong"   : 42,
                    "aDouble" : 42.42,
                    "aString" : "baz"
                ],
            ]
        ],
        "maps"      : [
            "str_str" : ["a": "b", "c": "d"],
            "str_bool": ["a": true, "b": false],
            "bool_int": [true: "10", false: "20"],
        ]
    ]

    /**
     * Creates config object from a map
     * @param map used for config source
     * @return config object
     */
    static Config createConfig(Map map = defaultCfgMap()) {
        ConfigFactory.parseMap(map)
    }

    /**
     * Returns default config map
     * @return map* @see #createConfigSource(java.util.Map)
     */
    static Map defaultCfgMap() {
        copy(defaultConfigMap)
    }

    /**
     * Deep-copies map
     * @param map map to copy
     * @return deep-copied map
     */
    static def copy(map) {
        def baos = new ByteArrayOutputStream()
        def oos = new ObjectOutputStream(baos)
        oos.writeObject(map); oos.flush()
        def bais = new ByteArrayInputStream(baos.toByteArray())
        def ois = new ObjectInputStream(bais)
        return ois.readObject()
    }

    /**
     * Creates new {@link Config} supplier backed by a map.
     * @param map map to use as a config source
     * @return config source
     * @see MapConfigSource
     */
    static MapConfigSource createConfigSource(Map map = defaultCfgMap()) {
        new MapConfigSource(map)
    }

    /**
     * {@link Supplier} of Config backed by a simple map that can be changed during the instance lifecycle.
     */
    static class MapConfigSource implements ConfigSource {
        private int fetches = 0
        private Map configMap

        /**
         * Creates new instance
         * @param cfg configuration as a map (default: empty map)
         * @see #set(java.util.Map)
         */
        MapConfigSource(Map cfg = [:]) {
            this.configMap = cfg
        }

        /**
         * Sets configuration map
         * @param cfg configuration map
         * @return reference to itself
         */
        MapConfigSource set(Map cfg) {
            this.configMap = cfg
            this
        }

        /**
         * Tells how many times configuration has been fetched.
         * @return number of fetches
         */
        int fetches() {
            fetches
        }

        @Override
        boolean allowErrors() {
            return false
        }

        @Override
        Config get(ConfigQuery query) throws RuntimeException {
            fetches++
            createConfig(configMap)
        }

        @Override
        void close() {
        }
    }
}
