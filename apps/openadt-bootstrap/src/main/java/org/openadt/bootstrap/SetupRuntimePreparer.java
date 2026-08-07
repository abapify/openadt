package org.openadt.bootstrap;

import org.openadt.config.CliLog;
import org.openadt.config.JCoJarCanonicalizer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class SetupRuntimePreparer {
    private static final String USER_HOME_PROPERTY = "user.home";
    private static final String OS_NAME_PROPERTY = "os.name";
    private static final String SOURCE_ARCHIVE_TEMPLATE =
        "https://github.com/abapify/openadt/archive/refs/tags/v%s.zip";
    private static final int PREPARE_TIMEOUT_MINUTES = 30;

    private SetupRuntimePreparer() {
    }

    public static boolean shouldPrepare(String adtPluginsDir) {
        return adtPluginsDir != null && !adtPluginsDir.isBlank();
    }

    public static int prepare(String adtPluginsDir, String version, boolean force)
        throws IOException, InterruptedException {
        Path pluginsDir = Path.of(adtPluginsDir);
        if (!Files.isDirectory(pluginsDir)) {
            CliLog.error("ADT plugins directory not found: " + adtPluginsDir);
            return 1;
        }
        Path runtimeDir = runtimeDir();
        Path outJar = runtimeDir.resolve("openadt-full.jar");
        if (!force && runtimeJarReady(version)) {
            CliLog.info("Runtime jar already prepared: " + outJar);
            return 0;
        }

        AdtBundleResolver.Resolution resolution = AdtBundleResolver.resolve(pluginsDir);
        if (!resolution.isComplete()) {
            CliLog.error("Missing SAP ADT / Eclipse bundles in " + adtPluginsDir + ":");
            resolution.missing().forEach(missing -> CliLog.error("  - " + missing));
            CliLog.error("Install the ABAP Development Tools feature in Eclipse, then re-run.");
            return 1;
        }
        resolution.drift()
            .forEach(drift -> CliLog.error("WARN: bundle version differs from tested baseline - " + drift));

        Path sourceDir = resolveSourceDir(version);
        if (!resolution.drift().isEmpty() && !supportsBundleProperties(sourceDir)) {
            reportUnresolvableDrift(version, resolution);
            return 1;
        }
        Files.createDirectories(runtimeDir);

        CliLog.info("Building full OpenADT runtime jar (first run may take a few minutes)...");
        int exitCode = runMavenBuild(sourceDir, pluginsDir, resolution.properties());
        if (exitCode != 0) {
            return exitCode;
        }
        return copyBuildOutputs(sourceDir, runtimeDir, outJar, version);
    }

    /**
     * Bundle overrides are passed as {@code -Dadt.jar.*}, which only bind if the source being built
     * routes its {@code systemPath} entries through those properties. Source archives released before
     * that change still pin exact file names, so drift cannot be absorbed there.
     */
    static boolean supportsBundleProperties(Path sourceDir) throws IOException {
        Path pom = sourceDir.resolve("apps/openadt-sap-adt/pom.xml");
        if (!Files.isRegularFile(pom)) {
            return false;
        }
        return Files.readString(pom).contains("${adt.jar.");
    }

    private static void reportUnresolvableDrift(String version, AdtBundleResolver.Resolution resolution) {
        CliLog.error("The OpenADT v" + version + " source archive pins exact SAP bundle versions, and the");
        CliLog.error("local plugin pool provides different ones:");
        resolution.drift().forEach(drift -> CliLog.error("  - " + drift));
        CliLog.error("");
        CliLog.error("Build from a checkout that resolves bundles dynamically instead:");
        CliLog.error("  git clone https://github.com/abapify/openadt && cd openadt");
        CliLog.error("  openadt config build --force");
    }

    // --- source resolution -------------------------------------------------

    /** Prefer an existing checkout so local changes are built; otherwise download the release tag. */
    private static Path resolveSourceDir(String version) throws IOException, InterruptedException {
        Optional<Path> checkout = findLocalCheckout();
        if (checkout.isPresent()) {
            CliLog.info("Building from local checkout: " + checkout.get());
            warnIfBuildOverwritesRunningJar(checkout.get());
            return checkout.get();
        }
        return downloadSource(version);
    }

    /**
     * Building the checkout rewrites {@code apps/openadt-cli/target}. When the running jar lives there,
     * the JVM loses classes it has not loaded yet and dies with a confusing {@link NoClassDefFoundError}
     * after the runtime jar is already prepared. Say so up front instead.
     */
    private static void warnIfBuildOverwritesRunningJar(Path checkout) {
        Path cliTarget = checkout.resolve("apps/openadt-cli/target").toAbsolutePath().normalize();
        boolean runningFromTarget = codeSourceDir()
            .map(dir -> dir.toAbsolutePath().normalize().startsWith(cliTarget))
            .orElse(false);
        if (runningFromTarget) {
            CliLog.error("WARN: this build replaces the jar you are running (" + cliTarget + ").");
            CliLog.error("WARN: the runtime jar will still be prepared, but this process may fail on exit.");
        }
    }

    static Optional<Path> findLocalCheckout() {
        List<Path> starts = new ArrayList<>();
        String userDir = System.getProperty("user.dir", "");
        if (!userDir.isBlank()) {
            starts.add(Path.of(userDir));
        }
        codeSourceDir().ifPresent(starts::add);

        for (Path start : starts) {
            for (Path current = start.toAbsolutePath().normalize(); current != null; current = current.getParent()) {
                if (isCheckoutRoot(current)) {
                    return Optional.of(current);
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isCheckoutRoot(Path candidate) {
        return Files.isRegularFile(candidate.resolve("pom.xml"))
            && Files.isRegularFile(candidate.resolve(mavenWrapperName()))
            && Files.isDirectory(candidate.resolve("apps/openadt-cli"));
    }

    private static Optional<Path> codeSourceDir() {
        try {
            URI codeSource = SetupRuntimePreparer.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI();
            Path jarOrClasses = Path.of(codeSource);
            return Optional.ofNullable(
                Files.isRegularFile(jarOrClasses) ? jarOrClasses.getParent() : jarOrClasses
            );
        } catch (Exception unresolvable) {
            return Optional.empty();
        }
    }

    private static Path downloadSource(String version) throws IOException, InterruptedException {
        Path buildRoot = buildRoot();
        Files.createDirectories(buildRoot);
        Path zipPath = buildRoot.resolve("openadt-" + version + ".zip");
        String url = String.format(SOURCE_ARCHIVE_TEMPLATE, version);

        CliLog.info("Downloading OpenADT v" + version + " source...");
        try (HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<Path> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofFile(zipPath)
            );
            if (response.statusCode() != 200) {
                throw new IOException("Download failed (HTTP " + response.statusCode() + "): " + url);
            }
        }

        Path extractRoot = buildRoot.resolve("src-" + version);
        deleteRecursively(extractRoot);
        unzip(zipPath, extractRoot);
        return findExtractedRoot(extractRoot);
    }

    private static Path findExtractedRoot(Path extractRoot) throws IOException {
        try (Stream<Path> stream = Files.list(extractRoot)) {
            return stream
                .filter(Files::isDirectory)
                .filter(SetupRuntimePreparer::isCheckoutRoot)
                .findFirst()
                .orElseThrow(() -> new IOException(
                    "Downloaded archive did not contain an OpenADT source root under " + extractRoot
                ));
        }
    }

    // --- archive handling --------------------------------------------------

    static void unzip(Path zipPath, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        Path normalizedTarget = targetDir.toAbsolutePath().normalize();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path resolved = normalizedTarget.resolve(entry.getName()).normalize();
                if (!resolved.startsWith(normalizedTarget)) {
                    throw new IOException("Archive entry escapes target directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                    continue;
                }
                Files.createDirectories(resolved.getParent());
                Files.copy(zip, resolved, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        restoreMavenWrapperPermissions(normalizedTarget);
    }

    /**
     * Zip entries do not reliably carry the POSIX executable bit, so an extracted {@code mvnw} cannot
     * be run. Restore it explicitly rather than failing later with "permission denied".
     */
    private static void restoreMavenWrapperPermissions(Path root) throws IOException {
        if (isWindows()) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root, 3)) {
            for (Path wrapper : stream.filter(path -> path.getFileName() != null
                && "mvnw".equals(path.getFileName().toString())
                && Files.isRegularFile(path)).toList()) {
                makeExecutable(wrapper);
            }
        }
    }

    private static void makeExecutable(Path file) throws IOException {
        try {
            Set<PosixFilePermission> permissions = new HashSet<>(Files.getPosixFilePermissions(file));
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            permissions.add(PosixFilePermission.GROUP_EXECUTE);
            permissions.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(file, permissions);
        } catch (UnsupportedOperationException notPosix) {
            // Non-POSIX filesystem; nothing to do.
        }
    }

    // --- maven ------------------------------------------------------------

    private static int runMavenBuild(Path sourceDir, Path pluginsDir, Map<String, Path> bundleProperties)
        throws IOException, InterruptedException {
        Path wrapper = sourceDir.resolve(mavenWrapperName());
        if (!isWindows()) {
            makeExecutable(wrapper);
        }

        List<String> command = new ArrayList<>();
        command.add(wrapper.toString());
        command.add("-q");
        command.add("-f");
        command.add("pom.xml");
        command.add("-pl");
        command.add("apps/openadt-cli");
        command.add("-am");
        command.add("package");
        command.add("-Dmaven.test.skip=true");
        command.add("-Dadt.plugins.dir=" + pluginsDir.toAbsolutePath());
        bundleProperties.forEach((name, path) -> command.add("-D" + name + "=" + path));

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(sourceDir.toFile());
        applyTrustedPath(builder);
        applyJavaHome(builder.environment());
        builder.inheritIO();
        Process process = builder.start();
        if (!process.waitFor(PREPARE_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            CliLog.error("Runtime prepare timed out.");
            return 1;
        }
        return process.exitValue();
    }

    private static String mavenWrapperName() {
        return isWindows() ? "mvnw.cmd" : "mvnw";
    }

    /**
     * The Maven wrapper finds a JDK through {@code JAVA_HOME} or {@code java} on {@code PATH}. Neither
     * is guaranteed: the packaged launcher invokes an absolute {@code java} binary without exporting
     * {@code JAVA_HOME}, and on macOS the {@code /usr/bin/java} stub then reports "Unable to locate a
     * Java Runtime". Point the child at the JDK already running this process.
     *
     * <p>An explicit {@code JAVA_HOME} in the environment is respected; only {@code PATH} is extended.
     */
    static void applyJavaHome(Map<String, String> environment) {
        String javaHome = System.getProperty("java.home", "");
        if (javaHome.isBlank()) {
            return;
        }
        environment.putIfAbsent("JAVA_HOME", javaHome);
        Path javaBin = Path.of(javaHome, "bin");
        String separator = isWindows() ? ";" : ":";
        String currentPath = environment.get("PATH");
        environment.put(
            "PATH",
            currentPath == null || currentPath.isBlank()
                ? javaBin.toString()
                : javaBin + separator + currentPath
        );
    }

    // --- outputs ----------------------------------------------------------

    private static int copyBuildOutputs(Path sourceDir, Path runtimeDir, Path outJar, String version)
        throws IOException {
        Path targetDir = sourceDir.resolve("apps/openadt-cli/target");
        Optional<Path> built = findBuiltJar(targetDir);
        if (built.isEmpty()) {
            CliLog.error("Maven build did not produce openadt-*.jar in " + targetDir);
            return 1;
        }
        Files.copy(built.get(), outJar, StandardCopyOption.REPLACE_EXISTING);

        Path sapLib = targetDir.resolve("sap-lib");
        if (Files.isDirectory(sapLib)) {
            Path runtimeSapLib = runtimeDir.resolve("sap-lib");
            deleteRecursively(runtimeSapLib);
            copySapLib(sapLib, runtimeSapLib);
            CliLog.info("Prepared runtime sap-lib: " + runtimeSapLib);
        }

        Files.writeString(runtimeDir.resolve("version.txt"), version, StandardCharsets.UTF_8);
        CliLog.info("Prepared runtime jar: " + outJar);
        return 0;
    }

    static Optional<Path> findBuiltJar(Path targetDir) throws IOException {
        if (!Files.isDirectory(targetDir)) {
            return Optional.empty();
        }
        try (Stream<Path> stream = Files.list(targetDir)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String name = path.getFileName().toString();
                    return name.startsWith("openadt-")
                        && name.endsWith(".jar")
                        && !name.contains("-sources")
                        && !name.contains("-javadoc");
                })
                .max(Comparator.comparing(SetupRuntimePreparer::lastModifiedOrEpoch));
        }
    }

    private static long lastModifiedOrEpoch(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException unreadable) {
            return 0L;
        }
    }

    /**
     * Copies the staged SAP bundles, restoring JCo's required archive name.
     *
     * <p>Maven names system-scope dependencies from their coordinates, so the JCo jar lands as
     * {@code jco-<version>.jar}. JCo refuses to initialize from that name — "It is not allowed to
     * rename or repackage the original archive" — so it is renamed back on the way in, leaving every
     * consumer of {@code sap-lib} with a directory it can put on the classpath verbatim.
     */
    static void copySapLib(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (Stream<Path> stream = Files.list(source)) {
            for (Path jar : stream.filter(Files::isRegularFile).toList()) {
                String name = jar.getFileName().toString();
                String canonical = JCoJarCanonicalizer.canonicalFileName(name);
                Files.copy(
                    jar,
                    target.resolve(canonical != null ? canonical : name),
                    StandardCopyOption.REPLACE_EXISTING
                );
            }
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }

    // --- locations --------------------------------------------------------

    private static Path runtimeDir() {
        return Path.of(System.getProperty(USER_HOME_PROPERTY), ".openadt", "runtime");
    }

    /** Platform cache location for the downloaded source tree and Maven output. */
    static Path buildRoot() {
        String home = System.getProperty(USER_HOME_PROPERTY, ".");
        String os = System.getProperty(OS_NAME_PROPERTY, "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            Path base = localAppData == null || localAppData.isBlank()
                ? Path.of(home, "AppData", "Local")
                : Path.of(localAppData);
            return base.resolve("openadt/build");
        }
        if (os.contains("mac")) {
            return Path.of(home, "Library", "Caches", "openadt", "build");
        }
        String xdgCache = System.getenv("XDG_CACHE_HOME");
        Path base = xdgCache == null || xdgCache.isBlank() ? Path.of(home, ".cache") : Path.of(xdgCache);
        return base.resolve("openadt/build");
    }

    public static String readInstalledVersion() throws IOException {
        String fromJar = readVersionFromRunningJar();
        if (fromJar != null && !fromJar.isBlank()) {
            return normalizeReleaseVersion(fromJar);
        }
        Path marker = runtimeDir().resolve("version.txt");
        if (Files.isRegularFile(marker)) {
            return Files.readString(marker, StandardCharsets.UTF_8).trim();
        }
        return "1.0.0";
    }

    private static String normalizeReleaseVersion(String version) {
        String trimmed = version.trim();
        int snapshot = trimmed.indexOf("-SNAPSHOT");
        if (snapshot > 0) {
            return trimmed.substring(0, snapshot);
        }
        return trimmed;
    }

    public static boolean runtimeJarReady(String version) {
        Path runtimeDir = runtimeDir();
        if (!Files.isRegularFile(runtimeDir.resolve("openadt-full.jar"))) {
            return false;
        }
        Path marker = runtimeDir.resolve("version.txt");
        if (!Files.isRegularFile(marker)) {
            return false;
        }
        try {
            return Files.readString(marker, StandardCharsets.UTF_8).trim().equals(version);
        } catch (IOException e) {
            return false;
        }
    }

    private static String readVersionFromRunningJar() {
        Package pkg = SetupRuntimePreparer.class.getPackage();
        if (pkg == null) {
            return null;
        }
        return pkg.getImplementationVersion();
    }

    private static boolean isWindows() {
        return System.getProperty(OS_NAME_PROPERTY, "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static void applyTrustedPath(ProcessBuilder builder) {
        String inheritedPath = builder.environment().getOrDefault("PATH", System.getenv("PATH"));
        if (isWindows()) {
            String systemRoot = System.getenv("SystemRoot");
            if (systemRoot == null || systemRoot.isBlank()) {
                systemRoot = "C:\\Windows";
            }
            String trustedPath = systemRoot + "\\System32\\WindowsPowerShell\\v1.0;"
                + systemRoot + "\\System32;"
                + systemRoot;
            builder.environment().put(
                "PATH",
                inheritedPath == null || inheritedPath.isBlank()
                    ? trustedPath
                    : trustedPath + ";" + inheritedPath
            );
            return;
        }
        builder.environment().put(
            "PATH",
            inheritedPath == null || inheritedPath.isBlank()
                ? "/usr/bin:/bin"
                : "/usr/bin:/bin:" + inheritedPath
        );
    }
}
