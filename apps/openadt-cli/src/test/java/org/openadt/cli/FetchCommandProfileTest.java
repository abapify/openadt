package org.openadt.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.openadt.config.SessionContext;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FetchCommandProfileTest {
    @Test
    void baseUrlWithProfileFailsCleanly() {
        CommandLine cmd = new CommandLine(new FetchCommand());
        int exitCode = cmd.execute(
            "--base-url",
            "https://dev-adt.example.com/sap/bc/adt",
            "--client",
            "100",
            "--path",
            "/sap/bc/adt/core/http/systeminformation",
            "--profile",
            "sso"
        );
        assertEquals(1, exitCode);
    }

    @Test
    void fetchWithoutSystemFailsWhenNoSessionContext(@TempDir Path temp) throws IOException {
        String fromEnv = System.getenv(SessionContext.SYSTEM_ENV);
        Assumptions.assumeTrue(
            fromEnv == null || fromEnv.isBlank(),
            "OPENADT_SYSTEM must be unset for this test"
        );
        Path config = temp.resolve("config.toml");
        Files.writeString(config, "[runtime]\n");
        CommandLine cmd = new CommandLine(new FetchCommand());
        int exitCode = cmd.execute("-c", config.toAbsolutePath().toString(), "/sap/bc/adt/core/discovery");
        assertEquals(1, exitCode);
    }

    @Test
    void parsesNoCacheFlag() {
        FetchCommand command = new FetchCommand();
        new CommandLine(command).parseArgs("DEV", "/sap/bc/adt/core/discovery", "--no-cache", "--profile", "sso");
        assertTrue(command.noCache);
    }
}
