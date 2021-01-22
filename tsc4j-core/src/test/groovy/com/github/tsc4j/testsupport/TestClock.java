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

package com.github.tsc4j.testsupport;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

/**
 * Test clock, useful for testing.
 */
@Accessors(chain = true)
public final class TestClock extends Clock {
    @Getter
    private final ZoneId zone;

    /**
     * Clock current timestamp.
     */
    @Setter
    private long timestamp;

    public TestClock() {
        this(System.currentTimeMillis());
    }

    public TestClock(long timestamp) {
        this(timestamp, ZoneId.systemDefault());
    }

    public TestClock(long timestamp, @NonNull ZoneId zone) {
        this.zone = zone;
        this.timestamp = timestamp;
    }

    public TestClock plus(long millis) {
        this.timestamp += millis;
        return this;
    }

    public TestClock plus(@NonNull Duration duration) {
        return plus(duration.toMillis());
    }

    public TestClock plus(long duration, @NonNull TimeUnit unit) {
        return plus(unit.toMillis(duration));
    }

    @Override
    public Clock withZone(@NonNull ZoneId zone) {
        return new TestClock(timestamp, zone);
    }

    @Override
    public long millis() {
        return timestamp;
    }

    @Override
    public Instant instant() {
        return Instant.ofEpochMilli(timestamp);
    }
}
