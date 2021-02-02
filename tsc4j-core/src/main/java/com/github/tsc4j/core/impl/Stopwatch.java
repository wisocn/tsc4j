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

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Value;
import lombok.val;

/**
 * Poor man's stopwatch.
 */
@Value
public class Stopwatch {
    @Getter(AccessLevel.NONE)
    long timeStarted = System.nanoTime();

    /**
     * Return elapsed duration in nanos.
     *
     * @return duration
     */
    long durationNanos() {
        return System.nanoTime() - timeStarted;
    }

    float durationMillis() {
        val duration = durationNanos();
        return ((float) duration) / (float) 1_000_000;
    }

    public static String toString(float durationMillis) {
        return String.format("%.2f msec", durationMillis);
    }

    @Override
    public String toString() {
        return toString(durationMillis());
    }
}
