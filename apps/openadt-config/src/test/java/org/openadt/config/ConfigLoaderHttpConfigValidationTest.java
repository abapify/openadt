package org.openadt.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderHttpConfigValidationTest {
    @Test
    void rejectsRemovedDiscoveryUrlKey(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("config.toml");
        Files.writeString(configFile, """
            version = 1

            [destinations.DEV.adt]
            discovery_url = "https://s4-dev.sap.example.com"
            """);

        OpenAdtException error = assertThrows(
            OpenAdtException.class,
            () -> new ConfigLoader(tempDir, tempDir).load(configFile)
        );
        assertTrue(error.getMessage().contains("discovery_url"));
        assertTrue(error.getMessage().contains("base_url"));
    }

    @Test
    void httpProfileRequiresBaseUrl(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("config.toml");
        Files.writeString(configFile, """
            version = 1

            [destinations.DEV]
            alias = "DEV"
            client = "100"

            [destinations.DEV.profiles.sso]
            transport = "http"
            authentication_kind = "browser-sso"
            """);

        OpenAdtException error = assertThrows(
            OpenAdtException.class,
            () -> new ConfigLoader(tempDir, tempDir).load(configFile)
        );
        assertTrue(error.getMessage().contains("profiles.sso"));
        assertTrue(error.getMessage().contains("base_url"));
    }

    @Test
    void acceptsHttpProfileWithBaseUrl(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("config.toml");
        Files.writeString(configFile, """
            version = 1

            [destinations.DEV]
            alias = "DEV"
            client = "100"

            [destinations.DEV.profiles.sso]
            transport = "http"
            authentication_kind = "browser-sso"
            base_url = "https://s4-dev.sap.example.com"
            """);

        OpenAdtConfig config = new ConfigLoader(tempDir, tempDir).load(configFile);

        assertEquals(
            "https://s4-dev.sap.example.com/",
            config.getSystems().get(0).getProfiles().get("sso").getBaseUrl()
        );
    }
}
