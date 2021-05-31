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

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Various {@link Runnable} utilities.
 */
@Slf4j
@UtilityClass
public class Runnables {
    /**
     * Safely runs given runnable.
     *
     * @param r runnable to run
     * @return true if runnable ran successfully, false if it threw exception
     */
    public boolean safeRun(Runnable r) {
        if (r == null) {
            return false;
        }

        try {
            r.run();
            return true;
        } catch (Throwable t) {
            log.warn("exception while running runnable {}: {}", r, t.getMessage(), t);
        }

        return false;
    }

    /**
     * Creates runnable that never throws.
     *
     * @param r runnable to wrap
     * @return safe runnable
     */
    public Runnable safeRunnable(@NonNull Runnable r) {
        return () -> safeRun(r);
    }
}
