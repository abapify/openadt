package org.openadt.setup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openadt.core.JCoJarCanonicalizer;
import org.openadt.core.OpenAdtConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RuntimeDetectorTest {
    @Test
    void prefersNewestJcoJarAndDetectsNativeLibraries(@TempDir Path tempDir) throws IOException {
        Path pluginsDir = tempDir.resolve("plugins");
        Files.createDirectories(pluginsDir);
        Files.writeString(pluginsDir.resolve("com.sap.conn.jco_3.1.12.jar"), "");
        Files.writeString(pluginsDir.resolve("com.sap.conn.jco_3.1.13.jar"), "");
        Files.writeString(pluginsDir.resolve("com.sap.adt.communication_3.58.0.jar"), "");
        Files.writeString(pluginsDir.resolve("com.sap.adt.destinations_3.58.0.jar"), "");
        Files.writeString(pluginsDir.resolve("com.sap.adt.destinations.model_3.58.0.jar"), "");
        Files.writeString(pluginsDir.resolve("com.sap.adt.compatibility_3.58.0.jar"), "");
        Files.writeString(pluginsDir.resolve("com.sap.adt.logging_3.58.0.jar"), "");
        Files.writeString(pluginsDir.resolve("com.sap.adt.util_3.58.0.jar"), "");

        Path nativeDir = tempDir.resolve("jco-native");
        Files.createDirectories(nativeDir);
        Files.writeString(nativeDir.resolve("sapjco3.dll"), "");

        Path sapcrypto = tempDir.resolve("SecureLogin/lib/sapcrypto.dll");
        Files.createDirectories(sapcrypto.getParent());
        Files.writeString(sapcrypto, "");

        Path jcoCache = tempDir.resolve("jco-cache");
        RuntimeDetector detector = new RuntimeDetector(
            List.of(pluginsDir),
            List.of(tempDir),
            List.of(sapcrypto),
            jcoCache
        );

        OpenAdtConfig.RuntimeConfig runtime = detector.detect();

        assertNotNull(runtime);
        Path expectedJco = JCoJarCanonicalizer.canonicalizeTo(
            pluginsDir.resolve("com.sap.conn.jco_3.1.13.jar"),
            jcoCache);
        assertEquals(expectedJco.toString(), runtime.getJcoJar());
        assertEquals(nativeDir.toString(), runtime.getJcoNativeDir());
        assertEquals(sapcrypto.toString(), runtime.getSapcrypto());
        assertEquals(pluginsDir.toString(), runtime.getAdtPluginsDir());
    }

    @Test
    void detectsMacOsNativeLibraries(@TempDir Path tempDir) throws IOException {
        Path pluginsDir = tempDir.resolve("plugins");
        Files.createDirectories(pluginsDir);
        Files.writeString(pluginsDir.resolve("com.sap.conn.jco_3.1.13.jar"), "");

        Path nativeDir = tempDir.resolve("jco-native");
        Files.createDirectories(nativeDir);
        Files.writeString(nativeDir.resolve("libsapjco3.dylib"), "");
        Files.writeString(nativeDir.resolve("libsapcrypto.dylib"), "");

        RuntimeDetector detector = new RuntimeDetector(
            List.of(pluginsDir),
            List.of(tempDir),
            List.of()
        );

        OpenAdtConfig.RuntimeConfig runtime = detector.detect();

        assertNotNull(runtime);
        assertEquals(nativeDir.toString(), runtime.getJcoNativeDir());
        assertEquals(nativeDir.resolve("libsapcrypto.dylib").toString(), runtime.getSapcrypto());
    }

    @Test
    void skipsNestedDevcontainerDistWhenScanningBroaderHostRoots(@TempDir Path tempDir) throws IOException {
        String originalUserDir = System.getProperty("user.dir");
        Path repoRoot = tempDir.resolve("repo");
        Path stagedNativeDir = repoRoot.resolve(".devcontainer/dist/jco");
        Path hostDocuments = tempDir.resolve("Documents");
        Path hostNativeDir = hostDocuments.resolve("Playground/jco-native");
        Path pluginsDir = tempDir.resolve("plugins");
        Files.createDirectories(stagedNativeDir);
        Files.createDirectories(hostNativeDir);
        Files.createDirectories(pluginsDir);
        Files.writeString(pluginsDir.resolve("com.sap.conn.jco_3.1.13.jar"), "");
        Files.writeString(stagedNativeDir.resolve("libsapjco3.so"), "");
        Files.writeString(hostNativeDir.resolve("sapjco3.dll"), "");

        try {
            System.setProperty("user.dir", repoRoot.toString());
            RuntimeDetector detector = new RuntimeDetector(
                List.of(pluginsDir),
                List.of(hostDocuments),
                List.of()
            );

            OpenAdtConfig.RuntimeConfig runtime = detector.detect();

            assertNotNull(runtime);
            assertEquals(hostNativeDir.toString(), runtime.getJcoNativeDir());
        } finally {
            if (originalUserDir != null) {
                System.setProperty("user.dir", originalUserDir);
            }
        }
    }
}
