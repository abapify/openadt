package org.openadt.cli;

import org.junit.jupiter.api.Test;
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
    void fetchWithoutSystemShowsUsage() {
        CommandLine cmd = new CommandLine(new FetchCommand());
        int exitCode = cmd.execute("/sap/bc/adt/core/discovery");
        assertEquals(1, exitCode);
    }

    @Test
    void parsesNoCacheFlag() {
        FetchCommand command = new FetchCommand();
        new CommandLine(command).parseArgs("DEV", "/sap/bc/adt/core/discovery", "--no-cache", "--profile", "sso");
        assertTrue(command.noCache);
    }
}
