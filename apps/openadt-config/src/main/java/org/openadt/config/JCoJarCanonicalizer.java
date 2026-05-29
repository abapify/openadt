package org.openadt.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * SAP JCo rejects renamed Eclipse p2 bundles ({@code com.sap.conn.jco_3.1.13.jar});
 * the archive file name must be {@code com.sap.conn.jco-&lt;version&gt;.jar}.
 */
public final class JCoJarCanonicalizer {
    private static final String SAP_PREFIX_UNDERSCORE = "com.sap.conn.jco_";
    private static final String SAP_PREFIX_HYPHEN = "com.sap.conn.jco-";
    private static final String SHORT_PREFIX = "jco-";

    private JCoJarCanonicalizer() {
    }

    public static String canonicalFileName(String fileName) {
        String version = extractVersion(fileName);
        if (version == null) {
            return null;
        }
        return "com.sap.conn.jco-" + version + ".jar";
    }

    public static Path canonicalize(Path source) throws IOException {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IOException("JCo jar not found: " + source);
        }
        String canonicalName = canonicalFileName(source.getFileName().toString());
        if (canonicalName == null) {
            return source;
        }
        if (canonicalName.equalsIgnoreCase(source.getFileName().toString())) {
            return source;
        }
        Path cacheDir = Path.of(System.getProperty("java.io.tmpdir")).resolve("openadt-jco-lib");
        Files.createDirectories(cacheDir);
        Path target = cacheDir.resolve(canonicalName);
        if (needsCopy(source, target)) {
            copyOrReuseExisting(source, target);
        }
        return target;
    }

    private static void copyOrReuseExisting(Path source, Path target) throws IOException {
        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException locked) {
            if (Files.exists(target)) {
                return;
            }
            throw locked;
        }
    }

    private static boolean needsCopy(Path source, Path target) throws IOException {
        if (!Files.exists(target)) {
            return true;
        }
        return Files.getLastModifiedTime(source).compareTo(Files.getLastModifiedTime(target)) > 0;
    }

    static boolean isJcoJar(Path path) {
        return path != null
            && path.getFileName() != null
            && extractVersion(path.getFileName().toString()) != null;
    }

    /** Copy into a fixed directory (for tests or isolated caches). */
    public static Path canonicalizeTo(Path source, Path targetDir) throws IOException {
        String canonicalName = canonicalFileName(source.getFileName().toString());
        if (canonicalName == null) {
            return source;
        }
        Files.createDirectories(targetDir);
        Path target = targetDir.resolve(canonicalName);
        if (needsCopy(source, target)) {
            copyOrReuseExisting(source, target);
        }
        return target;
    }

    private static String extractVersion(String fileName) {
        if (fileName == null) {
            return null;
        }
        String lower = fileName.toLowerCase();
        String prefix;
        if (lower.startsWith(SAP_PREFIX_UNDERSCORE)) {
            prefix = SAP_PREFIX_UNDERSCORE;
        } else if (lower.startsWith(SAP_PREFIX_HYPHEN)) {
            prefix = SAP_PREFIX_HYPHEN;
        } else if (lower.startsWith(SHORT_PREFIX)) {
            prefix = SHORT_PREFIX;
        } else {
            return null;
        }
        if (!lower.endsWith(".jar")) {
            return null;
        }
        String version = fileName.substring(prefix.length(), fileName.length() - 4);
        return isVersion(version) ? version : null;
    }

    private static boolean isVersion(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        boolean sawDot = false;
        int length = value.length();
        for (int i = 0; i < length; i++) {
            char c = value.charAt(i);
            if (c == '.') {
                if (i == 0 || i == length - 1 || value.charAt(i - 1) == '.') {
                    return false;
                }
                sawDot = true;
                continue;
            }
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return sawDot;
    }
}
