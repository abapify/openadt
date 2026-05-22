package org.openadt.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JCoJarCanonicalizerTest {
    @TempDir
    Path tempDir;

    @Test
    void renamesMavenCopiedJarForJCoRuntime() throws Exception {
        Path source = tempDir.resolve("jco-3.1.13.jar");
        Files.writeString(source, "stub");
        Path canonical = JCoJarCanonicalizer.canonicalizeTo(source, tempDir.resolve("cache"));
        assertEquals("com.sap.conn.jco-3.1.13.jar", canonical.getFileName().toString());
    }

    @Test
    void renamesEclipseP2JarForJCoRuntime() throws Exception {
        Path source = tempDir.resolve("com.sap.conn.jco_3.1.13.jar");
        Files.writeString(source, "stub");

        Path canonical = JCoJarCanonicalizer.canonicalizeTo(source, tempDir.resolve("cache"));

        assertEquals("com.sap.conn.jco-3.1.13.jar", canonical.getFileName().toString());
        assertTrue(Files.exists(canonical));
        assertEquals("com.sap.conn.jco-3.1.13.jar", JCoJarCanonicalizer.canonicalFileName(source.getFileName().toString()));
    }
}
