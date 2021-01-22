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

import com.github.tsc4j.core.Tsc4j;
import com.github.tsc4j.core.Tsc4jImplUtils;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.PicocliException;

import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Comparator;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Command line interface to tsc4j.
 *
 * @see TestCommand
 */
@Command(name = Tsc4jImplUtils.NAME,
    description = Tsc4jImplUtils.NAME + " command line interface",
    versionProvider = VersionProvider.class,
    sortOptions = false,
    mixinStandardHelpOptions = true,

    descriptionHeading = "%n",
    parameterListHeading = "%nPARAMETERS:%n",
    optionListHeading = "%nOPTIONS:%n",
    commandListHeading = "%nCOMMANDS:%n",

    synopsisSubcommandLabel = "COMMAND"
)
@Slf4j
@Value
@Builder(toBuilder = true)
public class Tsc4jCli implements Callable<Integer> {
    /**
     * STDIN stream.
     */
    @NonNull
    @Default
    InputStream stdin = System.in;

    /**
     * STDOUT stream.
     */
    @NonNull
    @Default
    PrintStream stdout = System.out;

    /**
     * STDERR stream
     */
    @NonNull
    @Default
    PrintStream stderr = System.err;

    /**
     * Call {@link System#exit(int)} when command is done? (default is <b>false</b>)
     */
    @Default
    boolean exitEnabled = false;

    /**
     * Should exceptions be catched by invocation handler?
     */
    @Default
    boolean catchRunExceptions = false;

    @NonFinal
    @Option(names = {"--full-version"}, description = "Prints full version information and exit.")
    private boolean fullVersion;

    private final AtomicBoolean used = new AtomicBoolean();

    /**
     * Cli starting point.
     *
     * @param args command line args.
     */
    public static void main(String... args) {
        val cli = Tsc4jCli.builder()
            .exitEnabled(true)
            .catchRunExceptions(true)
            .build();

        try {
            cli.run(args);
        } catch (PicocliException e) {
            cli.die("Error parsing command line: ", e.getMessage());
        }
    }

    /**
     * Runs the CLI.
     *
     * @param args command line arguments
     * @return command exit status in a sense of shell exit status code ({@code 0 => success, 1-255 => error})
     * @throws PicocliException in case of command line parsing errors
     * @throws RuntimeException in case of exceptions being thrown by subcommands
     * @see #run(String...)
     */
    public int run(@NonNull Collection<String> args) {
        return run(args.toArray(new String[0]));
    }

    /**
     * Runs the CLI.
     *
     * @param args command line arguments
     * @return command exit status in a sense of shell exit status code ({@code 0 => success, 1-255 => error})
     * @throws PicocliException in case of command line parsing errors
     * @throws Throwable        in case of exceptions being thrown by subcommands
     */
    public int run(@NonNull String... args) {
        if (!used.compareAndSet(false, true)) {
            throw new IllegalStateException("This instance cannot be reused for multiple invocations.");
        }

        log.debug("started {} with: {}", this, args);
        val cmdLine = new CommandLine(this);
        log.debug("created picocli cmdline", cmdLine);

        // load sub-commands, parse command line args and execute selected command
        return discoverSubCommands(cmdLine)
            .setCommandName(Tsc4jImplUtils.NAME)
            .setOut(new PrintWriter(this.stdout))
            .setErr(new PrintWriter(this.stderr))
            .setExecutionStrategy(new CommandLine.RunLast())
            .setParameterExceptionHandler(this::onInvalidParameter)
            .setExecutionExceptionHandler(this::handleExecutionException)
            .setExitCodeExceptionMapper(this::exitCodeMapper)
            .execute(args);
    }

    @Override
    public Integer call() {
        if (fullVersion) {
            printFullVersion();
            return 0;
        }
        return die("No command specified, please run with -h/--help for instructions");
    }

    /**
     * Discovers CLI sub-commands via {@link ServiceLoader} mechanism and registers them to cmdline instance.
     *
     * @param cmdLine cmdline instance.
     * @return passed cmdline instance
     * @see ServiceLoader#load(Class)
     * @see CliCommand
     */
    private CommandLine discoverSubCommands(CommandLine cmdLine) {
        Tsc4jImplUtils.loadImplementations(CliCommand.class)
            .stream()
            .sorted()
            .forEach(cmd -> {
                log.debug("adding sub-command: {}", cmd.getName());
                cmdLine.addSubcommand(cmd.getName(), cmd);
            });
        return cmdLine;
    }

    private int onInvalidParameter(CommandLine.ParameterException exception, String[] strings) {
        return handleExecutionException(exception, null, null);
    }

    /**
     * Picocli exception to exit status mapper
     *
     * @param throwable exception that occured.
     * @return exit status
     */
    private int exitCodeMapper(Throwable throwable) {
        if (throwable instanceof CommandLine.PicocliException) {
            return CommandLine.ExitCode.USAGE;
        }
        return CommandLine.ExitCode.SOFTWARE;
    }

    /**
     * Handles picocli execution exception.
     *
     * @param exception   exception
     * @param cmdLine     command line
     * @param parseResult cli parsing result
     * @return exit status
     */
    private int handleExecutionException(Exception exception, CommandLine cmdLine, ParseResult parseResult) {
        return handleExecutionException(exception, cmdLine);
    }

    /**
     * Handles exception that occurs in cli.
     *
     * @param exception exception
     * @param src       instance where exception occurred
     * @return exit status code
     */
    int handleException(@NonNull Throwable exception, Object src) {
        return handleExecutionException(exception, src);
    }

    private int handleExecutionException(@NonNull Throwable exception, Object src) {
        val shouldDisplayStackTrace = shouldDisplayStackTrace(src);
        val exMessage = exception.getMessage();
        boolean doStackTrace = shouldDisplayStackTrace || exMessage == null || exMessage.trim().length() < 20;

        val sb = new StringBuilder();
        sb.append("FATAL: ");

        if (doStackTrace) {
            val sw = new StringWriter();
            val pw = new PrintWriter(sw);
            exception.printStackTrace(pw);
            sb.append(sw.toString());
        } else {
            sb.append(exMessage.trim() + "\n");
        }

        stderr.print(sb);
        return exit(254);
    }

    private boolean shouldDisplayStackTrace(Object o) {
        if (o instanceof CommandLine) {
            return shouldDisplayStackTrace(((CommandLine) o).getCommand());
        } else if (o instanceof AbstractCommand) {
            return ((AbstractCommand) o).shouldDisplayStackTrace();
        }
        return false;
    }

    private void printFullVersion() {
        val out = getStdout();
        val props = Tsc4j.versionProperties();
        out.println(Tsc4jImplUtils.NAME + " " + Tsc4j.version() + "\n");
        props.entrySet().stream()
            .sorted(Comparator.comparing(a -> a.getKey().toString()))
            .forEach(e -> out.printf("%-30s %s\n", e.getKey(), e.getValue().toString().trim()));
    }

    /**
     * Exits JVM (if {@link #isExitEnabled()} returns true) with error message printed to stderr.
     *
     * @param messages error messages
     * @return nothing if {@link #isExitEnabled()} returns true because it exits JVM, otherwise exit status.
     * @see #exit(int)
     */
    int die(String... messages) {
        val msg = Stream.of(messages).collect(Collectors.joining());
        getStderr().println(msg);
        return exit(CommandLine.ExitCode.SOFTWARE);
    }

    /**
     * Exits JVM if {@link #isExitEnabled()} returns true.
     *
     * @param exitStatus desired exit status.
     * @return nothing if {@link #isExitEnabled()} returns true because it exits JVM, otherwise exit status.
     * @see #die(String...)
     */
    int exit(int exitStatus) {
        if (this.exitEnabled) {
            System.exit(exitStatus);
        }
        return exitStatus;
    }

    /**
     * Configures logger level.
     *
     * @param level desired log level.
     * @return true if log level was configured, otherwise false
     */
    boolean configureLogLevel(@NonNull String level) {
        if (isExitEnabled() && isCatchRunExceptions()) {
            return Tsc4jImplUtils.loadImplementations(LoggerConfigurer.class)
                .stream()
                .map(e -> e.configure(level))
                .filter(e -> e == true)
                .findFirst()
                .orElse(false);
        }
        return false;
    }
}
