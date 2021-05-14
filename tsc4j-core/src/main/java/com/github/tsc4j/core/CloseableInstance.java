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

package com.github.tsc4j.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base class for writing {@link Closeable} implementations.
 */
public abstract class CloseableInstance implements Closeable {
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Logger instance.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Tells whether instance is closed.
     *
     * @return true/false
     */
    public final boolean isClosed() {
        return closed.get();
    }

    /**
     * Throws if instance is closed.
     *
     * @throws IllegalStateException if instance is closed.
     * @see #close()
     */
    protected final void checkClosed() {
        if (isClosed()) {
            throw new IllegalStateException("Instance is closed: " + this);
        }
    }

    /**
     * Closes this instance and ensuring that it's closed only once; subsequent {@link #close()} invocations will result
     * in warning message being logged and {@link #checkClosed()} will throw {@link IllegalStateException}.
     */
    @Override
    @PreDestroy
    public final void close() {
        log.warn("XXX closing {}@{}", getClass().getName(), hashCode());

        if (!closed.compareAndSet(false, true)) {
            log.warn("{} attempting to close already closed instance.", this, new IllegalStateException("stacktrace"));
            return;
        }

        log.debug("closing: {}", this);
        doClose();
    }

    /**
     * Performs actual {@link #close()} functionality. Concrete implementations overriding this method should always
     * call super method.
     *
     * @see #close()
     * @see #isClosed()
     */
    protected void doClose() {
    }
}
