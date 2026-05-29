package org.openadt.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {
    @Test
    void testLoadValidToml(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.toml");
        String toml = """
            version = 1

            [runtime]
            jco_jar = "/path/to/jco.jar"
            jco_native_dir = "/path/to/native"
            adt_plugins_dir = "/path/to/eclipse/plugins"

            [proxy]
            listen = "127.0.0.1:8080"
            auth = "basic"
            username = "testuser"
            """;
        Files.writeString(configFile, toml);

        ConfigLoader loader = new ConfigLoader();
        OpenAdtConfig config = loader.load(configFile);

        assertEquals(1, config.getVersion());
        assertNotNull(config.getRuntime());
        assertEquals("/path/to/jco.jar", config.getRuntime().getJcoJar());
        assertEquals("/path/to/native", config.getRuntime().getJcoNativeDir());
        assertEquals("/path/to/eclipse/plugins", config.getRuntime().getAdtPluginsDir());
        assertNotNull(config.getProxy());
        assertEquals("127.0.0.1:8080", config.getProxy().getListen());
        assertEquals("basic", config.getProxy().getAuth());
        assertEquals("testuser", config.getProxy().getUsername());
    }

    @Test
    void testMissingFileReturnsEmpty(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("nonexistent.toml");
        ConfigLoader loader = new ConfigLoader();
        OpenAdtConfig config = loader.load(configFile);
        assertNotNull(config);
        assertEquals(0, config.getVersion());
    }

    @Test
    void testSecureLoginFieldMapping(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.toml");
        String toml = """
            version = 1

            [secure_login]
            local_security_hub = "https://127.0.0.1:34443"
            origin = "https://my.sap.system"
            referer = "https://my.sap.system/sap/bc/adt/"
            """;
        Files.writeString(configFile, toml);

        ConfigLoader loader = new ConfigLoader();
        OpenAdtConfig config = loader.load(configFile);

        assertNotNull(config.getSecureLogin());
        assertEquals("https://127.0.0.1:34443", config.getSecureLogin().getLocalSecurityHub());
        assertEquals("https://my.sap.system", config.getSecureLogin().getOrigin());
        assertEquals("https://my.sap.system/sap/bc/adt/", config.getSecureLogin().getReferer());
    }

    @Test
    void testRoundTrip(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.toml");
        OpenAdtConfig config = new OpenAdtConfig();
        config.setVersion(2);
        OpenAdtConfig.RuntimeConfig runtime = new OpenAdtConfig.RuntimeConfig();
        runtime.setJcoJar("/some/jco.jar");
        runtime.setAdtPluginsDir("/some/eclipse/plugins");
        config.setRuntime(runtime);

        ConfigLoader loader = new ConfigLoader();
        loader.save(config, configFile);

        OpenAdtConfig loaded = loader.load(configFile);
        assertEquals(2, loaded.getVersion());
        assertNotNull(loaded.getRuntime());
        assertEquals("/some/jco.jar", loaded.getRuntime().getJcoJar());
        assertEquals("/some/eclipse/plugins", loaded.getRuntime().getAdtPluginsDir());
    }

    @Test
    void loadsMergedConfigFromEntrypoint(@TempDir Path tempDir) throws IOException {
        Path configDir = tempDir.resolve(".openadt");
        Path entrypoint = configDir.resolve("config.toml");
        Path destinations = configDir.resolve("destinations/dev.openadt.toml");
        Path local = configDir.resolve("local.openadt.toml");
        Files.createDirectories(destinations.getParent());

        Files.writeString(entrypoint, """
            version = 1

            [merge]
            strategy = "last-wins"
            includes = [
              "destinations/*.openadt.toml",
              "local.openadt.toml"
            ]
            """);
        Files.writeString(destinations, """
            version = 1

            [destinations.DEV]
            system_id = "DEV"
            client = "100"
            language = "EN"

            [destinations.DEV.jco]
            mshost = "sap-ms"
            msserv = "3600"
            """);
        Files.writeString(local, """
            version = 1

            [runtime]
            jco_jar = "/opt/sap/sapjco3.jar"
            jco_native_dir = "/opt/sap"
            adt_plugins_dir = "/opt/eclipse/plugins"
            """);

        ConfigLoader loader = new ConfigLoader(tempDir, tempDir.resolve("home"));
        OpenAdtConfig config = loader.load(entrypoint);

        assertNotNull(config.getRuntime());
        assertEquals("/opt/sap/sapjco3.jar", config.getRuntime().getJcoJar());
        assertEquals("/opt/eclipse/plugins", config.getRuntime().getAdtPluginsDir());
        assertNotNull(config.getSystems());
        assertEquals(1, config.getSystems().size());
        assertEquals("DEV", config.getSystems().get(0).getAlias());
        assertEquals("100", config.getSystems().get(0).getClient());
        assertEquals("sap-ms", config.getSystems().get(0).getJco().getMshost());
    }

    @Test
    void saveSetupConfigWritesEntrypointAndFragments(@TempDir Path tempDir) throws IOException {
        Path homeDir = tempDir.resolve("home");
        Path configFile = homeDir.resolve(".openadt/config.toml");
        OpenAdtConfig config = new OpenAdtConfig();
        config.setVersion(1);

        OpenAdtConfig.RuntimeConfig runtime = new OpenAdtConfig.RuntimeConfig();
        runtime.setJcoJar("/opt/jco/sapjco3.jar");
        runtime.setAdtPluginsDir("/opt/eclipse/plugins");
        config.setRuntime(runtime);

        SystemProfile system = new SystemProfile();
        system.setAlias("DEV");
        system.setSystemId("DEV");
        system.setClient("100");
        config.setSystems(List.of(system));

        ConfigLoader loader = new ConfigLoader(tempDir.resolve("project"), homeDir);
        loader.saveSetupConfig(config, configFile);

        assertTrue(Files.exists(configFile));
        assertTrue(Files.exists(homeDir.resolve(".openadt/destinations/detected.openadt.toml")));
        assertTrue(Files.exists(homeDir.resolve(".openadt/local.openadt.toml")));

        OpenAdtConfig loaded = loader.load(configFile);
        assertNotNull(loaded.getRuntime());
        assertEquals("/opt/jco/sapjco3.jar", loaded.getRuntime().getJcoJar());
        assertEquals("/opt/eclipse/plugins", loaded.getRuntime().getAdtPluginsDir());
        assertNotNull(loaded.getSystems());
        assertEquals("DEV", loaded.getSystems().get(0).getAlias());
    }

    @Test
    void prefersProjectLocalConfigOverHomeConfig(@TempDir Path tempDir) throws IOException {
        Path projectDir = tempDir.resolve("project");
        Path homeDir = tempDir.resolve("home");
        Path localConfig = projectDir.resolve(".openadt/config.toml");
        Path globalConfig = homeDir.resolve(".openadt/config.toml");
        Files.createDirectories(localConfig.getParent());
        Files.createDirectories(globalConfig.getParent());
        Files.writeString(localConfig, "version = 1\n");
        Files.writeString(globalConfig, "version = 2\n");

        ConfigLoader loader = new ConfigLoader(projectDir, homeDir);

        assertEquals(localConfig, loader.getDefaultConfigPath());
        assertEquals(1, loader.load().getVersion());
    }

    @Test
    void fallsBackToHomeConfigWhenProjectLocalIsMissing(@TempDir Path tempDir) throws IOException {
        Path projectDir = tempDir.resolve("project");
        Path homeDir = tempDir.resolve("home");
        Path globalConfig = homeDir.resolve(".openadt/config.toml");
        Files.createDirectories(globalConfig.getParent());
        Files.writeString(globalConfig, "version = 2\n");

        ConfigLoader loader = new ConfigLoader(projectDir, homeDir);

        assertEquals(globalConfig, loader.getDefaultConfigPath());
        assertEquals(2, loader.load().getVersion());
    }

    @Test
    void defaultsToProjectLocalPathWhenNoConfigExists(@TempDir Path tempDir) {
        Path projectDir = tempDir.resolve("project");
        Path homeDir = tempDir.resolve("home");

        ConfigLoader loader = new ConfigLoader(projectDir, homeDir);

        assertEquals(projectDir.resolve(".openadt/config.toml"), loader.getDefaultConfigPath());
    }

    @Test
    void environmentConfigOverridesSearchPaths(@TempDir Path tempDir) throws IOException {
        Path projectDir = tempDir.resolve("project");
        Path homeDir = tempDir.resolve("home");
        Path envConfig = tempDir.resolve("devcontainer/openadt-config.toml");
        Files.createDirectories(envConfig.getParent());
        Files.writeString(envConfig, "version = 7\n");

        ConfigLoader loader = new ConfigLoader(projectDir, homeDir, envConfig);

        assertEquals(envConfig, loader.getDefaultConfigPath());
        assertEquals(7, loader.load().getVersion());
    }

    @Test
    void mergesHttpCallbackHostFromIncludedFragments(@TempDir Path tempDir) throws IOException {
        Path configDir = tempDir.resolve(".openadt");
        Path entrypoint = configDir.resolve("config.toml");
        Path runtimeFragment = configDir.resolve("local.openadt.toml");
        Files.createDirectories(configDir);

        Files.writeString(entrypoint, """
            version = 1

            [merge]
            strategy = "last-wins"
            includes = ["local.openadt.toml"]
            """);
        Files.writeString(runtimeFragment, """
            version = 1

            [runtime]
            http_callback_host = "localhost"
            """);

        ConfigLoader loader = new ConfigLoader(tempDir, tempDir.resolve("home"));
        OpenAdtConfig config = loader.load(entrypoint);

        assertEquals("localhost", config.getRuntime().getHttpCallbackHost());
    }

    @Test
    void setupDefaultsToHomeConfigPath(@TempDir Path tempDir) {
        Path projectDir = tempDir.resolve("project");
        Path homeDir = tempDir.resolve("home");

        ConfigLoader loader = new ConfigLoader(projectDir, homeDir);

        assertEquals(homeDir.resolve(".openadt/config.toml"), loader.getDefaultSetupConfigPath());
    }
}
