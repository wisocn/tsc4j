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

import lombok.val;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Base class for writing {@link Closeable} implementations.
 */
public abstract class CloseableInstance implements Closeable {
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Logger instance.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private volatile String closedBy;

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
     * Tells whether {@link #close()} should issue warning if instance was already closed. This method is meant to be
     * overriden by the actual implementations.
     *
     * @return true/false
     */
    protected boolean warnIfAlreadyClosed() {
        return true;
    }

    /**
     * Closes this instance and ensuring that it's closed only once; subsequent {@link #close()} invocations will result
     * in warning message being logged and {@link #checkClosed()} will throw {@link IllegalStateException}.
     */
    @Override
    @PreDestroy
    public final void close() {
        if (!closed.compareAndSet(false, true)) {
            val stackTrace = new IllegalStateException("Futile close() attempt stacktrace");
            val closedBy = getClosedBy();
            val message = "{} attempting to close already closed instance.{}";

            if (warnIfAlreadyClosed()) {
                log.warn(message, this, closedBy, stackTrace);
            } else {
                log.debug(message, this, closedBy, stackTrace);
            }

            // nothing else to do.
            return;
        }

        log.debug("closing instance: {}", this);
        this.closedBy = createClosedBy();

        try {
            doClose();
            log.debug("successfully closed instance: {}", this);
        } catch (Throwable t) {
            log.debug("exception while closing instance: {}", this, t);
            throw t;
        }
    }

    private String getClosedBy() {
        val str = this.closedBy;
        return (str == null) ? "" : " Instance was closed by:\n  " + str;
    }

    private String createClosedBy() {
        val exception = new RuntimeException("close() stacktrace");
        return Arrays.stream(exception.getStackTrace())
            .skip(3)
            .map(it -> it.toString())
            .collect(Collectors.joining("\n  "));
    }

    /**
     * Performs actual {@link #close()} functionality that is being invoked in atomic manner. Concrete implementations
     * overriding this method should always call super method.
     *
     * @see #close()
     * @see #isClosed()
     */
    protected void doClose() {
    }
}
