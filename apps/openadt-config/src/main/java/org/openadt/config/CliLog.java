package org.openadt.config;

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

    /** Diagnostics (SSO steps, URLs). Off by default; set {@code OPENADT_VERBOSE=true}. */
    public static void diagnostic(String message) {
        if (verbose()) {
            error(message);
        }
    }

    /** HTTP SSO diagnostics (no secrets). Off by default; set {@code OPENADT_VERBOSE=true}. */
    public static void httpSso(String message) {
        if (verbose()) {
            error("[openadt http-sso] " + message);
        }
    }

    /** SDK/JCo/SNC bootstrap diagnostics. Off by default; set {@code OPENADT_VERBOSE=true}. */
    public static void sdk(String message) {
        if (verbose()) {
            error("[openadt sdk] " + message);
        }
    }

    public static boolean verbose() {
        return Boolean.parseBoolean(System.getenv().getOrDefault("OPENADT_VERBOSE", "false"));
    }

    public static PrintStream stdout() {
        return System.out;
    }

    public static PrintStream stderr() {
        return System.err;
    }
}
