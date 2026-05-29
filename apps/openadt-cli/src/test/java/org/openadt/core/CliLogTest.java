package org.openadt.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliLogTest {
    private static final PrintStream ORIGINAL_ERR = System.err;

    @AfterEach
    void restoreStderr() {
        System.setErr(ORIGINAL_ERR);
    }

    @Test
    void errorsAlwaysPrintRegardlessOfVerbose() {
        Assumptions.assumeFalse(CliLog.verbose());
        ByteArrayOutputStream err = captureStderr();
        CliLog.error("fetch failed");
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("fetch failed"));
    }

    @Test
    void sdkHttpSsoAndDiagnosticQuietWhenNotVerbose() {
        Assumptions.assumeFalse(CliLog.verbose());
        ByteArrayOutputStream err = captureStderr();
        CliLog.sdk("bootstrap ready");
        CliLog.httpSso("cache hit");
        CliLog.diagnostic("proxy tip");
        assertEquals("", err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void sdkHttpSsoAndDiagnosticPrintWhenVerbose() {
        Assumptions.assumeTrue(CliLog.verbose());
        ByteArrayOutputStream err = captureStderr();
        CliLog.sdk("bootstrap ready");
        CliLog.httpSso("cache hit");
        CliLog.diagnostic("proxy tip");
        String output = err.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("[openadt sdk] bootstrap ready"));
        assertTrue(output.contains("[openadt http-sso] cache hit"));
        assertTrue(output.contains("proxy tip"));
    }

    private static ByteArrayOutputStream captureStderr() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        return err;
    }
}
