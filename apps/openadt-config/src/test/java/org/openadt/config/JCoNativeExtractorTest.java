package org.openadt.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JCoNativeExtractorTest {
    @TempDir
    Path tempDir;

    /** The bundle name for whichever platform the test happens to run on. */
    private static String platformBundleName(String version) {
        return JCoNativeExtractor.bundlePrefix() + version + ".jar";
    }

    private static boolean platformSupported() {
        return JCoNativeExtractor.bundlePrefix() != null && JCoNativeExtractor.nativeLibraryName() != null;
    }

    private Path writeBundle(Path pluginsDir, String version, String entryPath, String content) throws Exception {
        Files.createDirectories(pluginsDir);
        Path bundle = pluginsDir.resolve(platformBundleName(version));
        try (OutputStream out = Files.newOutputStream(bundle);
             ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zip.write("Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(entryPath));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bundle;
    }

    @Test
    void extractsNativeFromPlatformBundle() throws Exception {
        if (!platformSupported()) {
            return;
        }
        Path pluginsDir = tempDir.resolve("plugins");
        Path cacheDir = tempDir.resolve("cache");
        String nativeName = JCoNativeExtractor.nativeLibraryName();
        writeBundle(pluginsDir, "3.1.12", "lib/" + nativeName, "native-bytes");

        Optional<Path> extracted = JCoNativeExtractor.extractFrom(List.of(pluginsDir), cacheDir);

        assertTrue(extracted.isPresent());
        // Cached per bundle identity, so the parent directory carries the bundle name and version.
        String bundleKey = platformBundleName("3.1.12").replace(".jar", "");
        assertEquals(cacheDir.resolve(bundleKey).resolve(nativeName), extracted.get());
        assertEquals("native-bytes", Files.readString(extracted.get()));
    }

    @Test
    void findsNativeAtArchiveRootToo() throws Exception {
        if (!platformSupported()) {
            return;
        }
        Path pluginsDir = tempDir.resolve("plugins");
        Path cacheDir = tempDir.resolve("cache");
        String nativeName = JCoNativeExtractor.nativeLibraryName();
        writeBundle(pluginsDir, "3.1.12", nativeName, "root-level");

        Optional<Path> extracted = JCoNativeExtractor.extractFrom(List.of(pluginsDir), cacheDir);

        assertTrue(extracted.isPresent());
        assertEquals("root-level", Files.readString(extracted.get()));
    }

    @Test
    void picksNewestBundleVersion() throws Exception {
        if (!platformSupported()) {
            return;
        }
        Path pluginsDir = tempDir.resolve("plugins");
        Path cacheDir = tempDir.resolve("cache");
        String nativeName = JCoNativeExtractor.nativeLibraryName();
        writeBundle(pluginsDir, "3.1.12", "lib/" + nativeName, "older");
        writeBundle(pluginsDir, "3.1.13", "lib/" + nativeName, "newer");

        Optional<Path> extracted = JCoNativeExtractor.extractFrom(List.of(pluginsDir), cacheDir);

        assertTrue(extracted.isPresent());
        assertEquals("newer", Files.readString(extracted.get()));
    }

    @Test
    void reusesExtractedCopyWhenBundleUnchanged() throws Exception {
        if (!platformSupported()) {
            return;
        }
        Path pluginsDir = tempDir.resolve("plugins");
        Path cacheDir = tempDir.resolve("cache");
        String nativeName = JCoNativeExtractor.nativeLibraryName();
        writeBundle(pluginsDir, "3.1.12", "lib/" + nativeName, "first");

        Path first = JCoNativeExtractor.extractFrom(List.of(pluginsDir), cacheDir).orElseThrow();
        // Overwrite the cached copy; an unchanged bundle must not trigger re-extraction.
        Files.writeString(first, "cached-marker");
        Path second = JCoNativeExtractor.extractFrom(List.of(pluginsDir), cacheDir).orElseThrow();

        assertEquals(first, second);
        assertEquals("cached-marker", Files.readString(second));
    }

    @Test
    void doesNotServeAnotherBundlesNativeWhenTimestampsAreNotMonotonic() throws Exception {
        if (!platformSupported()) {
            return;
        }
        Path cacheDir = tempDir.resolve("cache");
        String nativeName = JCoNativeExtractor.nativeLibraryName();

        Path newerPool = tempDir.resolve("pool-newer");
        writeBundle(newerPool, "3.1.13", "lib/" + nativeName, "from-3.1.13");
        Path fromNewer = JCoNativeExtractor.extractFrom(List.of(newerPool), cacheDir).orElseThrow();
        assertEquals("from-3.1.13", Files.readString(fromNewer));

        // A different bundle, backdated so an mtime comparison would consider the cache fresh.
        Path olderPool = tempDir.resolve("pool-older");
        Path olderBundle = writeBundle(olderPool, "3.1.12", "lib/" + nativeName, "from-3.1.12");
        Files.setLastModifiedTime(olderBundle, FileTime.fromMillis(0L));

        Path fromOlder = JCoNativeExtractor.extractFrom(List.of(olderPool), cacheDir).orElseThrow();

        // Each bundle gets its own cache entry, so neither can serve the other's native.
        assertNotEquals(fromNewer, fromOlder);
        assertEquals("from-3.1.12", Files.readString(fromOlder));
        assertEquals("from-3.1.13", Files.readString(fromNewer));
    }

    @Test
    void leavesNoPartialFilesBehind() throws Exception {
        if (!platformSupported()) {
            return;
        }
        Path pluginsDir = tempDir.resolve("plugins");
        Path cacheDir = tempDir.resolve("cache");
        String nativeName = JCoNativeExtractor.nativeLibraryName();
        writeBundle(pluginsDir, "3.1.12", "lib/" + nativeName, "bytes");

        Path extracted = JCoNativeExtractor.extractFrom(List.of(pluginsDir), cacheDir).orElseThrow();

        try (var entries = Files.list(extracted.getParent())) {
            assertEquals(List.of(nativeName), entries.map(p -> p.getFileName().toString()).toList());
        }
    }

    @Test
    void returnsEmptyWhenNoBundlePresent() {
        Path pluginsDir = tempDir.resolve("empty");
        assertTrue(JCoNativeExtractor.extractFrom(List.of(pluginsDir), tempDir.resolve("cache")).isEmpty());
    }

    @Test
    void returnsEmptyWhenBundleLacksNative() throws Exception {
        if (!platformSupported()) {
            return;
        }
        Path pluginsDir = tempDir.resolve("plugins");
        writeBundle(pluginsDir, "3.1.12", "lib/unrelated.txt", "nothing-useful");

        assertTrue(JCoNativeExtractor.extractFrom(List.of(pluginsDir), tempDir.resolve("cache")).isEmpty());
    }

    @Test
    void ignoresBundlesForOtherPlatforms() throws Exception {
        if (!platformSupported()) {
            return;
        }
        Path pluginsDir = tempDir.resolve("plugins");
        Files.createDirectories(pluginsDir);
        // A bundle for a platform that is definitely not the current one.
        String foreignPrefix = JCoNativeExtractor.bundlePrefix().contains("macosx")
            ? "com.sap.conn.jco.linux.x86_64_"
            : "com.sap.conn.jco.macosx.aarch64_";
        Files.createFile(pluginsDir.resolve(foreignPrefix + "3.1.13.jar"));

        assertTrue(JCoNativeExtractor.extractFrom(List.of(pluginsDir), tempDir.resolve("cache")).isEmpty());
    }

    @Test
    void nativeNameMatchesPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String nativeName = JCoNativeExtractor.nativeLibraryName();
        if (os.contains("mac")) {
            assertEquals("libsapjco3.dylib", nativeName);
        } else if (os.contains("linux")) {
            assertEquals("libsapjco3.so", nativeName);
        } else if (os.contains("win")) {
            assertEquals("sapjco3.dll", nativeName);
        }
        assertFalse(nativeName != null && nativeName.isBlank());
    }
}
