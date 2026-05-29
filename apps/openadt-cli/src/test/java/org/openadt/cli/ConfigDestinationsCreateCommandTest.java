package org.openadt.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigDestinationsCreateCommandTest {
    @Test
    void nonInteractiveCreationWritesProfileConfig(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("config.toml");
        OpenAdtCommand root = new OpenAdtCommand();
        CommandLine cmd = new CommandLine(root);

        int exitCode = cmd.execute(
            "config",
            "destinations",
            "create",
            "--config",
            configFile.toString(),
            "--alias",
            "DEV",
            "--profile",
            "sso",
            "--transport",
            "http",
            "--auth",
            "browser-sso",
            "--base-url",
            "https://dev-adt.example.com",
            "--browser-entry-url",
            "https://idp.example.corp/app/sap/sso/saml",
            "--client",
            "100",
            "--language",
            "EN",
            "--default-profile"
        );

        assertEquals(0, exitCode);
        assertTrue(Files.exists(configFile));
        String contents = Files.readString(tempDir.resolve("destinations/manual.openadt.toml"));
        assertTrue(contents.contains("[destinations.\"DEV\".profiles.\"sso\"]"));
        assertTrue(contents.contains("browser_entry_url = \"https://idp.example.corp/app/sap/sso/saml\""));
        assertTrue(contents.contains("default_profile"));
    }

    @Test
    void missingRequiredFlagsWithoutConsoleFailsWithActionableMessage() {
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        java.io.PrintStream originalErr = System.err;
        System.setErr(new java.io.PrintStream(err, true, java.nio.charset.StandardCharsets.UTF_8));
        try {
            OpenAdtCommand root = new OpenAdtCommand();
            CommandLine cmd = new CommandLine(root);

            int exitCode = cmd.execute("config", "destinations", "create", "--alias", "DEV");

            assertEquals(1, exitCode);
            String message = err.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(message.contains("Missing required options"));
            assertTrue(message.contains("--profile"));
            assertTrue(message.contains("--client"));
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    void repeatedCreationUpdatesExistingProfile(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("config.toml");
        OpenAdtCommand root = new OpenAdtCommand();
        CommandLine cmd = new CommandLine(root);

        String[] baseArgs = {
            "config", "destinations", "create",
            "--config", configFile.toString(),
            "--alias", "DEV",
            "--profile", "sso",
            "--transport", "http",
            "--auth", "browser-sso",
            "--client", "100",
            "--language", "EN"
        };

        assertEquals(0, cmd.execute(concat(baseArgs, "--base-url", "https://first.example.com")));
        assertEquals(0, cmd.execute(concat(baseArgs, "--base-url", "https://dev-adt.example.com")));

        String contents = Files.readString(tempDir.resolve("destinations/manual.openadt.toml"));
        assertTrue(contents.contains("base_url = \"https://dev-adt.example.com/\""));
        assertFalse(contents.contains("https://first.example.com"));
    }

    private static String[] concat(String[] base, String... extra) {
        String[] result = new String[base.length + extra.length];
        System.arraycopy(base, 0, result, 0, base.length);
        System.arraycopy(extra, 0, result, base.length, extra.length);
        return result;
    }
}
