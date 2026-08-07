package org.openadt.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetupRuntimePreparerTest {
    @TempDir
    Path tempDir;

    private void withPlatform(String osName, Path home, Runnable body) {
        String originalOs = System.getProperty("os.name");
        String originalHome = System.getProperty("user.home");
        try {
            System.setProperty("os.name", osName);
            System.setProperty("user.home", home.toString());
            body.run();
        } finally {
            restore("os.name", originalOs);
            restore("user.home", originalHome);
        }
    }

    private void restore(String key, String value) {
        if (value != null) {
            System.setProperty(key, value);
        } else {
            System.clearProperty(key);
        }
    }

    @Test
    void usesLibraryCachesOnMacOs() {
        withPlatform("Mac OS X", tempDir, () ->
            assertEquals(tempDir.resolve("Library/Caches/openadt/build"), SetupRuntimePreparer.buildRoot())
        );
    }

    @Test
    void usesXdgOrDotCacheOnLinux() {
        withPlatform("Linux", tempDir, () -> {
            Path buildRoot = SetupRuntimePreparer.buildRoot();
            String xdg = System.getenv("XDG_CACHE_HOME");
            if (xdg == null || xdg.isBlank()) {
                assertEquals(tempDir.resolve(".cache/openadt/build"), buildRoot);
            } else {
                assertEquals(Path.of(xdg).resolve("openadt/build"), buildRoot);
            }
        });
    }

    @Test
    void usesLocalAppDataOnWindows() {
        withPlatform("Windows 11", tempDir, () -> {
            Path buildRoot = SetupRuntimePreparer.buildRoot();
            String localAppData = System.getenv("LOCALAPPDATA");
            Path expectedBase = localAppData == null || localAppData.isBlank()
                ? tempDir.resolve("AppData/Local")
                : Path.of(localAppData);
            assertEquals(expectedBase.resolve("openadt/build"), buildRoot);
        });
    }

    @Test
    void rejectsArchiveEntriesEscapingTargetDirectory() throws Exception {
        Path zipPath = tempDir.resolve("evil.zip");
        try (OutputStream out = Files.newOutputStream(zipPath);
             ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("../escaped.txt"));
            zip.write("pwned".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Path target = tempDir.resolve("extract");

        IOException error = assertThrows(
            IOException.class,
            () -> SetupRuntimePreparer.unzip(zipPath, target)
        );
        assertTrue(error.getMessage().contains("escapes target directory"));
        assertTrue(Files.notExists(tempDir.resolve("escaped.txt")));
    }

    @Test
    void extractsNestedEntriesAndRestoresMavenWrapperExecutableBit() throws Exception {
        Path zipPath = tempDir.resolve("src.zip");
        try (OutputStream out = Files.newOutputStream(zipPath);
             ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("openadt-1.2.3/"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("openadt-1.2.3/mvnw"));
            zip.write("#!/bin/sh\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("openadt-1.2.3/apps/openadt-cli/pom.xml"));
            zip.write("<project/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Path target = tempDir.resolve("extract");

        SetupRuntimePreparer.unzip(zipPath, target);

        Path wrapper = target.resolve("openadt-1.2.3/mvnw");
        assertTrue(Files.isRegularFile(wrapper));
        assertTrue(Files.isRegularFile(target.resolve("openadt-1.2.3/apps/openadt-cli/pom.xml")));
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            assertTrue(
                Files.getPosixFilePermissions(wrapper).contains(PosixFilePermission.OWNER_EXECUTE),
                "extracted mvnw must be executable or the build cannot start"
            );
        }
    }

    @Test
    void findsNewestBuiltJarIgnoringSourcesAndJavadoc() throws Exception {
        Path target = tempDir.resolve("target");
        Files.createDirectories(target);
        Files.createFile(target.resolve("openadt-2.1.2-sources.jar"));
        Files.createFile(target.resolve("openadt-2.1.2-javadoc.jar"));
        Path real = Files.createFile(target.resolve("openadt-2.1.2.jar"));

        Optional<Path> found = SetupRuntimePreparer.findBuiltJar(target);

        assertTrue(found.isPresent());
        assertEquals(real.getFileName(), found.get().getFileName());
    }

    @Test
    void findsNoJarInEmptyOrMissingTarget() throws Exception {
        assertTrue(SetupRuntimePreparer.findBuiltJar(tempDir.resolve("absent")).isEmpty());
        Path empty = Files.createDirectories(tempDir.resolve("empty"));
        assertTrue(SetupRuntimePreparer.findBuiltJar(empty).isEmpty());
    }

    @Test
    void detectsCheckoutRootFromWorkingDirectory() throws Exception {
        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo.resolve("apps/openadt-cli"));
        Files.createFile(repo.resolve("pom.xml"));
        Files.createFile(repo.resolve("mvnw"));
        Files.createFile(repo.resolve("mvnw.cmd"));
        Path nested = Files.createDirectories(repo.resolve("apps/openadt-cli/src"));

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", nested.toString());
            Optional<Path> found = SetupRuntimePreparer.findLocalCheckout();
            assertTrue(found.isPresent());
            assertEquals(repo.toAbsolutePath().normalize(), found.get());
        } finally {
            restore("user.dir", originalUserDir);
        }
    }

    @Test
    void reportsMissingPluginsDirectory() throws Exception {
        int exitCode = SetupRuntimePreparer.prepare(tempDir.resolve("absent").toString(), "1.2.3", true);
        assertEquals(1, exitCode);
    }

    @Test
    void refusesIncompleteBundlePoolBeforeBuilding() throws Exception {
        Path pluginsDir = Files.createDirectories(tempDir.resolve("plugins"));
        // Directory exists but holds none of the required bundles.
        int exitCode = SetupRuntimePreparer.prepare(pluginsDir.toString(), "1.2.3", true);
        assertEquals(1, exitCode);
    }

    @Test
    void pointsChildBuildAtTheRunningJdkWhenJavaHomeIsUnset() {
        Map<String, String> environment = new HashMap<>();
        environment.put("PATH", "/usr/bin:/bin");

        SetupRuntimePreparer.applyJavaHome(environment);

        String javaHome = System.getProperty("java.home");
        assertEquals(javaHome, environment.get("JAVA_HOME"));
        assertTrue(
            environment.get("PATH").startsWith(Path.of(javaHome, "bin").toString()),
            "the running JDK's bin must precede the macOS /usr/bin/java stub"
        );
        assertTrue(environment.get("PATH").contains("/usr/bin:/bin"));
    }

    @Test
    void respectsExplicitJavaHome() {
        Map<String, String> environment = new HashMap<>();
        environment.put("JAVA_HOME", "/opt/chosen-jdk");

        SetupRuntimePreparer.applyJavaHome(environment);

        assertEquals("/opt/chosen-jdk", environment.get("JAVA_HOME"));
    }

    @Test
    void setsPathEvenWhenEnvironmentHasNone() {
        Map<String, String> environment = new HashMap<>();

        SetupRuntimePreparer.applyJavaHome(environment);

        assertTrue(environment.get("PATH").contains("bin"));
    }

    @Test
    void detectsWhetherSourceRoutesSystemPathsThroughProperties() throws Exception {
        Path modern = tempDir.resolve("modern/apps/openadt-sap-adt");
        Files.createDirectories(modern);
        Files.writeString(modern.resolve("pom.xml"), "<systemPath>${adt.jar.communication}</systemPath>");

        Path released = tempDir.resolve("released/apps/openadt-sap-adt");
        Files.createDirectories(released);
        Files.writeString(
            released.resolve("pom.xml"),
            "<systemPath>${adt.plugins.dir}/com.sap.adt.communication_3.58.0.jar</systemPath>"
        );

        assertTrue(SetupRuntimePreparer.supportsBundleProperties(tempDir.resolve("modern")));
        assertTrue(!SetupRuntimePreparer.supportsBundleProperties(tempDir.resolve("released")));
        assertTrue(!SetupRuntimePreparer.supportsBundleProperties(tempDir.resolve("absent")));
    }

    @Test
    void restoresJcoArchiveNameWhenStagingSapLib() throws Exception {
        Path staged = Files.createDirectories(tempDir.resolve("target/sap-lib"));
        // Maven names system-scope deps from coordinates, which repackages JCo under a name it rejects.
        Files.createFile(staged.resolve("jco-3.1.13.jar"));
        Files.createFile(staged.resolve("jco-eclipse-1.32.0.jar"));
        Files.createFile(staged.resolve("communication-3.58.0.jar"));
        Path runtimeSapLib = tempDir.resolve("runtime/sap-lib");

        SetupRuntimePreparer.copySapLib(staged, runtimeSapLib);

        assertTrue(
            Files.isRegularFile(runtimeSapLib.resolve("com.sap.conn.jco-3.1.13.jar")),
            "JCo refuses to initialize unless the archive keeps its original name"
        );
        assertTrue(Files.notExists(runtimeSapLib.resolve("jco-3.1.13.jar")));
        // Only the JCo core archive is name-sensitive; everything else is copied verbatim.
        assertTrue(Files.isRegularFile(runtimeSapLib.resolve("jco-eclipse-1.32.0.jar")));
        assertTrue(Files.isRegularFile(runtimeSapLib.resolve("communication-3.58.0.jar")));
    }

    @Test
    void shouldPrepareOnlyWithConfiguredPluginsDir() {
        assertTrue(SetupRuntimePreparer.shouldPrepare("/some/path"));
        assertTrue(!SetupRuntimePreparer.shouldPrepare(null));
        assertTrue(!SetupRuntimePreparer.shouldPrepare("  "));
    }
}
