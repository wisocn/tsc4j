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

import lombok.NonNull;
import lombok.val;

/**
 * Exception that doesn't fill stacktrace.
 */
public final class Tsc4jException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates new exception with message.
     *
     * @param message exception message, must be non-null
     * @param cause   cause, must be non-null
     */
    private Tsc4jException(@NonNull String message, @NonNull Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates new instance with cause formatted message.
     *
     * @param fmt   message format, first placeholder {@code %%s} is replaced with {@code cause} message, otherwise with
     *              formatted args.
     * @param cause exception cause
     * @param args  {@code fmt} format args
     * @return exception
     * @see String#format(String, Object...)
     */
    public static Tsc4jException of(@NonNull String fmt, @NonNull Throwable cause, Object... args) {
        val msgFmt = String.format(fmt, args);
        val errMsg = (cause.getMessage() == null) ? cause.toString() : cause.getMessage();
        val msg = String.format(msgFmt.replaceFirst("%%s", "%s"), errMsg);
        return new Tsc4jException(msg, cause);
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }
}
