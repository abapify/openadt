package org.openadt.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * On macOS and Linux the JCo native library is not shipped as a loose file: Eclipse delivers it
 * inside a platform-specific p2 bundle (for example {@code com.sap.conn.jco.macosx.aarch64_3.1.12.jar}
 * containing {@code lib/libsapjco3.dylib}). Scanning the filesystem therefore never finds it.
 *
 * <p>This extractor locates the bundle matching the running platform and unpacks the native into a
 * stable directory that can be handed to JCo as {@code java.library.path}.
 */
public final class JCoNativeExtractor {
    private static final String BUNDLE_PREFIX_MAC_AARCH64 = "com.sap.conn.jco.macosx.aarch64_";
    private static final String BUNDLE_PREFIX_MAC_X86_64 = "com.sap.conn.jco.macosx.x86_64_";
    private static final String BUNDLE_PREFIX_LINUX_X86_64 = "com.sap.conn.jco.linux.x86_64_";
    private static final String BUNDLE_PREFIX_WIN_X86_64 = "com.sap.conn.jco.win32.x86_64_";

    private static final String NATIVE_MAC = "libsapjco3.dylib";
    private static final String NATIVE_LINUX = "libsapjco3.so";
    private static final String NATIVE_WINDOWS = "sapjco3.dll";

    private JCoNativeExtractor() {
    }

    /**
     * Extracts the JCo native for the running platform out of a p2 plugin pool.
     *
     * @param pluginRoots directories to scan for the platform bundle
     * @return directory containing the extracted native, or empty when no matching bundle exists
     */
    public static Optional<Path> extractFrom(List<Path> pluginRoots) {
        return extractFrom(pluginRoots, defaultCacheDir());
    }

    /** Extract into a caller-supplied directory (for tests or isolated caches). */
    public static Optional<Path> extractFrom(List<Path> pluginRoots, Path cacheDir) {
        String nativeName = nativeLibraryName();
        if (nativeName == null) {
            return Optional.empty();
        }
        Optional<Path> bundle = findPlatformBundle(pluginRoots);
        if (bundle.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(extractNative(bundle.get(), nativeName, cacheDir));
        } catch (IOException error) {
            return Optional.empty();
        }
    }

    private static Path extractNative(Path bundle, String nativeName, Path cacheDir) throws IOException {
        Path target = cacheDir.resolve(nativeName);
        if (!needsExtract(bundle, target)) {
            return target;
        }
        Files.createDirectories(cacheDir);
        try (ZipFile zip = new ZipFile(bundle.toFile())) {
            ZipEntry entry = findNativeEntry(zip, nativeName);
            if (entry == null) {
                throw new IOException("Bundle " + bundle.getFileName() + " does not contain " + nativeName);
            }
            try (InputStream input = zip.getInputStream(entry)) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return target;
    }

    /**
     * Matches the native by file name anywhere in the bundle. SAP has shipped it under {@code lib/}
     * and at the archive root across versions, so the directory is not assumed.
     */
    private static ZipEntry findNativeEntry(ZipFile zip, String nativeName) {
        return zip.stream()
            .filter(entry -> !entry.isDirectory())
            .filter(entry -> {
                String name = entry.getName();
                int slash = name.lastIndexOf('/');
                String leaf = slash < 0 ? name : name.substring(slash + 1);
                return leaf.equalsIgnoreCase(nativeName);
            })
            .findFirst()
            .orElse(null);
    }

    private static boolean needsExtract(Path bundle, Path target) throws IOException {
        if (!Files.isRegularFile(target)) {
            return true;
        }
        return Files.getLastModifiedTime(bundle).compareTo(Files.getLastModifiedTime(target)) > 0;
    }

    /** Newest platform bundle across all roots, matched by prefix so any JCo version works. */
    static Optional<Path> findPlatformBundle(List<Path> pluginRoots) {
        String prefix = bundlePrefix();
        if (prefix == null) {
            return Optional.empty();
        }
        Path newest = null;
        for (Path root : pluginRoots) {
            Optional<Path> best = bestInRoot(root, prefix);
            if (best.isPresent() && (newest == null || compareBundleVersions(best.get(), newest, prefix) > 0)) {
                newest = best.get();
            }
        }
        return Optional.ofNullable(newest);
    }

    private static Optional<Path> bestInRoot(Path root, String prefix) {
        if (!Files.isDirectory(root)) {
            return Optional.empty();
        }
        Path newest = null;
        try (Stream<Path> stream = Files.list(root)) {
            for (Path candidate : stream.filter(Files::isRegularFile).toList()) {
                String name = candidate.getFileName().toString();
                if (name.startsWith(prefix) && name.endsWith(".jar")
                    && (newest == null || compareBundleVersions(candidate, newest, prefix) > 0)) {
                    newest = candidate;
                }
            }
        } catch (IOException ignored) {
            // Best-effort discovery only.
        }
        return Optional.ofNullable(newest);
    }

    private static int compareBundleVersions(Path left, Path right, String prefix) {
        return OsgiVersions.compare(
            bundleVersion(left, prefix),
            bundleVersion(right, prefix)
        );
    }

    private static String bundleVersion(Path path, String prefix) {
        String name = path.getFileName().toString();
        return name.substring(prefix.length(), name.length() - ".jar".length());
    }

    static String bundlePrefix() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean aarch64 = arch.contains("aarch64") || arch.contains("arm64");
        if (os.contains("mac") || os.contains("darwin")) {
            return aarch64 ? BUNDLE_PREFIX_MAC_AARCH64 : BUNDLE_PREFIX_MAC_X86_64;
        }
        if (os.contains("linux")) {
            return aarch64 ? null : BUNDLE_PREFIX_LINUX_X86_64;
        }
        if (os.contains("win")) {
            return BUNDLE_PREFIX_WIN_X86_64;
        }
        return null;
    }

    static String nativeLibraryName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac") || os.contains("darwin")) {
            return NATIVE_MAC;
        }
        if (os.contains("linux")) {
            return NATIVE_LINUX;
        }
        if (os.contains("win")) {
            return NATIVE_WINDOWS;
        }
        return null;
    }

    private static Path defaultCacheDir() {
        return Path.of(System.getProperty("user.home", "."), ".openadt", "runtime", "jco-native");
    }

    /** Plugin roots to scan, derived from an already-detected JCo jar or plugins directory. */
    public static List<Path> pluginRootsFor(Path... candidates) {
        List<Path> roots = new ArrayList<>();
        for (Path candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            Path root = Files.isDirectory(candidate) ? candidate : candidate.getParent();
            if (root != null && !roots.contains(root)) {
                roots.add(root);
            }
        }
        return roots;
    }
}
