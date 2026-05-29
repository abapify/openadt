package org.openadt.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigLoaderHttpOverrideTest {
    @Test
    void entrypointHttpTransportSurvivesDetectedSdk(@TempDir Path tempDir) throws Exception {
        Path detected = tempDir.resolve("detected.openadt.toml");
        Path entrypoint = tempDir.resolve("config.toml");
        Files.writeString(detected, """
            version = 1

            [destinations."DEV"]
            system_id = "DEV"

            [destinations."DEV".adt]
            transport = "sdk"
            discovery_url = "https://example.com/sap/bc/adt"
            """);
        Files.writeString(entrypoint, """
            version = 1

            [merge]
            strategy = "last-wins"
            includes = ["detected.openadt.toml"]

            [destinations."DEV".adt]
            transport = "http"
            """);

        ConfigLoader loader = new ConfigLoader(tempDir, tempDir);
        OpenAdtConfig config = loader.load(entrypoint);
        SystemProfile dev = config.getSystems().stream()
            .filter(s -> "DEV".equals(s.getAlias()))
            .findFirst()
            .orElseThrow();
        assertEquals("http", dev.getAdt().getTransport());
    }
}
