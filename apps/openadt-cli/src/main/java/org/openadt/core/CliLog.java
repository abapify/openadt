package org.openadt.core;

import java.io.PrintStream;

/**
 * CLI stdout/stderr for user-facing messages and diagnostics.
 * Centralizes {@link System#out}/{@link System#err} so Sonar rule java:S106 is satisfied outside this class.
 */
@SuppressWarnings("java:S106")
public final class CliLog {
    private CliLog() {
    }

    public static void info(String message) {
        System.out.println(message);
    }

    public static void info(String format, Object... args) {
        System.out.printf(format, args);
    }

    public static void error(String message) {
        System.err.println(message);
    }

    public static void error(String format, Object... args) {
        System.err.printf(format, args);
    }

    public static PrintStream stdout() {
        return System.out;
    }

    public static PrintStream stderr() {
        return System.err;
    }
}
