package org.openadt.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigLoaderProfileHttpCaCertTest {
    @Test
    void profileHttpCaCertSurvivesFragmentMerge(@TempDir Path tempDir) throws Exception {
        Path manual = tempDir.resolve("manual.openadt.toml");
        Path entrypoint = tempDir.resolve("config.toml");
        Files.writeString(manual, """
            version = 1

            [destinations."DEV"]
            alias = "DEV"
            default_profile = "sso"

            [destinations."DEV".profiles."sso"]
            transport = "http"
            authentication_kind = "browser-sso"
            discovery_url = "https://abap.example.invalid/sap/bc/adt"
            http_ca_cert = "C:\\\\landscape\\\\frontend.pem"
            """);
        Files.writeString(entrypoint, """
            version = 1

            [merge]
            strategy = "last-wins"
            includes = ["manual.openadt.toml"]
            """);

        ConfigLoader loader = new ConfigLoader(tempDir, tempDir);
        OpenAdtConfig config = loader.load(entrypoint);
        SystemProfile effective = DestinationProfileResolver.resolve(config, "DEV", "sso");

        assertEquals("C:\\landscape\\frontend.pem", effective.getAdt().getHttpCaCert());
    }
}
