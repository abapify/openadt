package org.openadt.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderProfileTest {
    @Test
    void parsesDestinationProfiles(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.toml");
        Files.writeString(configFile, """
            version = 1

            [destinations.DEV]
            alias = "DEV"
            client = "100"
            language = "EN"
            default_profile = "snc"

            [destinations.DEV.jco]
            mshost = "dev-ms.example.com"
            msserv = "3600"

            [destinations.DEV.profiles.sso]
            transport = "http"
            authentication_kind = "browser-sso"
            discovery_url = "https://dev-adt.example.com/sap/bc/adt"
            callback_port = "0"

            [destinations.DEV.profiles.snc]
            transport = "sdk"
            authentication_kind = "snc"

            [destinations.DEV.profiles.snc.jco]
            snc_mode = "1"
            snc_qop = "9"
            snc_partnername = "p:CN=SAPServiceDEV"
            """);

        ConfigLoader loader = new ConfigLoader();
        OpenAdtConfig config = loader.load(configFile);

        SystemProfile dev = config.getSystems().get(0);
        assertEquals("snc", dev.getDefaultProfile());
        assertNotNull(dev.getProfiles());
        assertEquals("http", dev.getProfiles().get("sso").getTransport());
        assertEquals("9", dev.getProfiles().get("snc").getJco().getSncQop());
    }

    @Test
    void mergedProfileFragmentsUseLastWins(@TempDir Path tempDir) throws IOException {
        Path configDir = tempDir.resolve(".openadt");
        Path entrypoint = configDir.resolve("config.toml");
        Path first = configDir.resolve("destinations/a.openadt.toml");
        Path second = configDir.resolve("destinations/b.openadt.toml");
        Files.createDirectories(first.getParent());

        Files.writeString(entrypoint, """
            version = 1

            [merge]
            strategy = "last-wins"
            includes = ["destinations/*.openadt.toml"]
            """);
        Files.writeString(first, """
            version = 1

            [destinations.DEV.profiles.sso]
            transport = "http"
            discovery_url = "https://first.example.com/sap/bc/adt"
            """);
        Files.writeString(second, """
            version = 1

            [destinations.DEV.profiles.sso]
            discovery_url = "https://dev-adt.example.com/sap/bc/adt"
            """);

        ConfigLoader loader = new ConfigLoader(tempDir, tempDir.resolve("home"));
        OpenAdtConfig config = loader.load(entrypoint);

        assertEquals(
            "https://dev-adt.example.com/sap/bc/adt",
            config.getSystems().get(0).getProfiles().get("sso").getDiscoveryUrl()
        );
        assertEquals("http", config.getSystems().get(0).getProfiles().get("sso").getTransport());
    }

    @Test
    void saveManualDestinationProfileWritesProfileFragment(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.toml");
        ConfigLoader loader = new ConfigLoader(tempDir, tempDir.resolve("home"));

        SystemProfile destination = new SystemProfile();
        destination.setAlias("DEV");
        destination.setClient("100");
        destination.setLanguage("EN");

        SystemProfile.ProfileConfig profile = new SystemProfile.ProfileConfig();
        profile.setTransport("http");
        profile.setAuthenticationKind("browser-sso");
        profile.setDiscoveryUrl("https://dev-adt.example.com/sap/bc/adt");

        Path written = loader.saveManualDestinationProfile(configFile, destination, "sso", profile, true);

        assertTrue(Files.exists(configFile));
        assertTrue(Files.exists(written));
        OpenAdtConfig loaded = loader.load(configFile);
        SystemProfile dev = loaded.getSystems().stream()
            .filter(system -> "DEV".equals(system.getAlias()))
            .findFirst()
            .orElseThrow();
        assertEquals("sso", dev.getDefaultProfile());
        assertEquals("http", dev.getProfiles().get("sso").getTransport());
    }

    @Test
    void saveManualDestinationProfilePreservesFlatConfigSections(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.toml");
        Files.writeString(configFile, """
            version = 1

            [runtime]
            jco_jar = "/path/to/jco.jar"

            [proxy]
            listen = "127.0.0.1:8080"
            auth = "basic"

            [destinations.DEV]
            alias = "DEV"
            client = "100"
            """);

        ConfigLoader loader = new ConfigLoader(tempDir, tempDir.resolve("home"));
        SystemProfile destination = new SystemProfile();
        destination.setAlias("DEV");
        destination.setClient("100");
        destination.setLanguage("EN");

        SystemProfile.ProfileConfig profile = new SystemProfile.ProfileConfig();
        profile.setTransport("http");
        profile.setAuthenticationKind("browser-sso");
        profile.setDiscoveryUrl("https://dev-adt.example.com/sap/bc/adt");

        loader.saveManualDestinationProfile(configFile, destination, "sso", profile, true);

        OpenAdtConfig loaded = loader.load(configFile);
        assertEquals("/path/to/jco.jar", loaded.getRuntime().getJcoJar());
        assertEquals("127.0.0.1:8080", loaded.getProxy().getListen());
        assertEquals("sso", loaded.getSystems().get(0).getDefaultProfile());
        assertEquals("http", loaded.getSystems().get(0).getProfiles().get("sso").getTransport());
    }

    @Test
    void repeatedSaveUpdatesExistingProfile(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.toml");
        ConfigLoader loader = new ConfigLoader(tempDir, tempDir.resolve("home"));

        SystemProfile destination = new SystemProfile();
        destination.setAlias("DEV");
        destination.setClient("100");
        destination.setLanguage("EN");

        SystemProfile.ProfileConfig first = new SystemProfile.ProfileConfig();
        first.setTransport("http");
        first.setDiscoveryUrl("https://first.example.com/sap/bc/adt");
        loader.saveManualDestinationProfile(configFile, destination, "sso", first, false);

        SystemProfile.ProfileConfig updated = new SystemProfile.ProfileConfig();
        updated.setTransport("http");
        updated.setDiscoveryUrl("https://dev-adt.example.com/sap/bc/adt");
        loader.saveManualDestinationProfile(configFile, destination, "sso", updated, true);

        OpenAdtConfig loaded = loader.load(configFile);
        SystemProfile dev = loaded.getSystems().get(0);
        assertEquals("sso", dev.getDefaultProfile());
        assertEquals(
            "https://dev-adt.example.com/sap/bc/adt",
            dev.getProfiles().get("sso").getDiscoveryUrl()
        );
    }
}
