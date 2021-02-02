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

package com.github.tsc4j.cli;

import com.github.tsc4j.core.BaseInstance;
import com.github.tsc4j.core.Tsc4jConfig;
import lombok.ToString;
import lombok.val;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.ParentCommand;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.Objects;

import static com.github.tsc4j.core.Tsc4jImplUtils.optString;

/**
 * Base class for writing tsc4j cli sub-commands
 */
@ToString
@Command(sortOptions = false)
public abstract class AbstractCommand extends BaseInstance implements CliCommand {
    /**
     * Parent command
     */
    @ParentCommand
    private Tsc4jCli parent = null;

    /**
     * Common options
     */
    @Mixin
    //@CommandLine.ArgGroup(exclusive = false) // results in:
    // results in:
    // NullPointerException: null while processing argument at or before arg[0] 'get' in [get, -h]: java.lang.NullPointerException
    private CommonOptions commonOptions = null;

    /**
     * Creates instance.
     */
    public AbstractCommand() {
        super("command");
    }

    /**
     * Returns stdout stream.
     *
     * @return print stream
     */
    protected PrintStream getStdout() {
        return getParent().getStdout();
    }

    /**
     * Returns stderr stream
     *
     * @return print stream
     */
    protected PrintStream getStderr() {
        return getParent().getStderr();
    }

    /**
     * Returns stdin input stream
     *
     * @return input stream
     */
    protected InputStream getStdin() {
        return getParent().getStdin();
    }

    /**
     * Tells whether command should produce as little output as possible.
     *
     * @see #isVerbose()
     */
    protected boolean isQuiet() {
        return getCommonOptions().isQuiet();
    }

    /**
     * Tells whether verbose output is enabled
     *
     * @return true/false
     * @see #verbosityLevel()
     * @see #isQuiet()
     */
    protected boolean isVerbose() {
        return getCommonOptions().isVerbose();
    }

    /**
     * Returns verbosity level.
     *
     * @return verbosity level; if 0 verbose output is not enabled.
     * @see #isVerbose()
     */
    protected int verbosityLevel() {
        return getCommonOptions().verbosityLevel();
    }

    /**
     * Tells whether stacktrace should be displayed in case of exceptions.
     *
     * @return true/false
     */
    boolean shouldDisplayStackTrace() {
        return getCommonOptions().isStacktrace();
    }

    /**
     * Returns actual tsc4j bootstrap configuration
     *
     * @return tsc4j bootstrap configuration
     */
    protected Tsc4jConfig getConfig() {
        return getCommonOptions().getConfig();
    }

    /**
     * Finishes command execution with a fatal error
     *
     * @param fmt  error format message in a form of {@link String#format(String, Object...)}
     * @param args format arguments
     * @return exit status
     */
    protected int die(String fmt, Object... args) {
        return die(String.format(fmt, args));
    }

    /**
     * Finishes command execution with a fatal error
     *
     * @param messages error message
     * @return exit status
     */
    protected int die(String... messages) {
        return getParent().die(messages);
    }

    /**
     * Configures logger log level.
     *
     * @param level log level
     * @return true/false
     */
    protected final boolean configureLogLevel(String level) {
        return optString(level)
            .map(e -> getParent().configureLogLevel(e))
            .orElse(false);
    }

    /**
     * Configures logger log level according to command line arguments
     *
     * @see CommonOptions#getLogLevel()
     */
    private void configureLogLevel() {
        configureLogLevel(getCommonOptions().getLogLevel());
    }

    @Override
    public final Integer call() {
        // always try to configure log level
        configureLogLevel();

        try {
            return doCall();
        } catch (Throwable t) {
            // picocli 4.x doesn't seem to handle exceptions correctly
            // via setExecutionExceptionHandler(), so we need to do it manually :-/
            return getParent().handleException(t, this);
        }
    }

    /**
     * Invokes real business logic of the command.
     *
     * @return exit status.
     */
    protected abstract int doCall();

    /**
     * Returns parent command.
     *
     * @return parent command
     * @throws NullPointerException if parent command is not defined.
     */
    protected final Tsc4jCli getParent() {
        return Objects.requireNonNull(parent, "Parent instance is not set.");
    }

    /**
     * Returns common options instance.
     *
     * @return common options
     * @throws NullPointerException if common options is not defined.
     */
    protected final CommonOptions getCommonOptions() {
        return Objects.requireNonNull(commonOptions, "CommonOptions instance is not set.");
    }

    @Override
    public final String getType() {
        return "cli-command";
    }

    @Override
    public int compareTo(CliCommand other) {
        if (other == null) {
            return 1;
        }

        // by group
        val byGroup = getGroup().compareTo(other.getGroup());
        if (byGroup != 0) {
            return byGroup;
        }

        // by name
        return getName().compareTo(other.getName());
    }
}
