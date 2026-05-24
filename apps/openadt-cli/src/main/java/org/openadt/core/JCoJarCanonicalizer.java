package org.openadt.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SAP JCo rejects renamed Eclipse p2 bundles ({@code com.sap.conn.jco_3.1.13.jar});
 * the archive file name must be {@code com.sap.conn.jco-&lt;version&gt;.jar}.
 */
public final class JCoJarCanonicalizer {
    private static final Pattern JCO_JAR_NAME = Pattern.compile(
        "(?:com\\.sap\\.conn\\.jco[_-]|jco-)(\\d+(?:\\.\\d+)+)\\.jar",
        Pattern.CASE_INSENSITIVE
    );

    private JCoJarCanonicalizer() {
    }

    public static String canonicalFileName(String fileName) {
        Matcher matcher = JCO_JAR_NAME.matcher(fileName);
        if (!matcher.matches()) {
            return null;
        }
        return "com.sap.conn.jco-" + matcher.group(1) + ".jar";
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
            && JCO_JAR_NAME.matcher(path.getFileName().toString()).matches();
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
}
