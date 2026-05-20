package org.openadt.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
        config.setRuntime(runtime);

        ConfigLoader loader = new ConfigLoader();
        loader.save(config, configFile);

        OpenAdtConfig loaded = loader.load(configFile);
        assertEquals(2, loaded.getVersion());
        assertNotNull(loaded.getRuntime());
        assertEquals("/some/jco.jar", loaded.getRuntime().getJcoJar());
    }
}
