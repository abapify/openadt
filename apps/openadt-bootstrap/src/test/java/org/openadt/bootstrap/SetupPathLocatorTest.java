package org.openadt.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SetupPathLocatorTest {
    @Test
    void includesStagedDevcontainerRuntimeRoots(@TempDir Path tempDir) throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        Path stagedJcoDir = tempDir.resolve(".devcontainer/dist/jco");
        Path stagedSncDir = tempDir.resolve(".devcontainer/dist/snc");
        Files.createDirectories(stagedJcoDir);
        Files.createDirectories(stagedSncDir);

        try {
            System.setProperty("user.dir", tempDir.toString());

            assertTrue(SetupPathLocator.jcoJarRoots().contains(stagedJcoDir));
            assertTrue(SetupPathLocator.jcoNativeSearchRoots().contains(stagedJcoDir));
            assertTrue(SetupPathLocator.jcoNativeSearchRoots().contains(stagedSncDir));
            assertTrue(SetupPathLocator.sapcryptoCandidates().contains(stagedSncDir.resolve("libsapcrypto.so")));
        } finally {
            if (originalUserDir != null) {
                System.setProperty("user.dir", originalUserDir);
            }
        }
    }
}
