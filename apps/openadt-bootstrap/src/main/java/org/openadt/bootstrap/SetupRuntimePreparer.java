package org.openadt.bootstrap;

import org.openadt.config.CliLog;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
public final class SetupRuntimePreparer {
    private static final String USER_HOME_PROPERTY = "user.home";

    private SetupRuntimePreparer() {
    }

    public static boolean shouldPrepare(String adtPluginsDir) {
        return adtPluginsDir != null && !adtPluginsDir.isBlank();
    }

    public static int prepare(String adtPluginsDir, String version, boolean force) throws IOException, InterruptedException {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
            CliLog.error("Automatic runtime prepare is supported on Windows only.");
            CliLog.error("Use scripts/openadt-sdk.ps1 from a git checkout for fetch/proxy.");
            return 1;
        }
        Path home = openAdtHome();
        Path script = home.resolve("bin/prepare-openadt-runtime.ps1");
        if (!Files.isRegularFile(script)) {
            CliLog.error("Missing runtime prepare script: " + script);
            CliLog.error("Reinstall OpenADT or run from a git checkout.");
            return 1;
        }
        String powershellExe = windowsSystemExecutable("System32\\WindowsPowerShell\\v1.0\\powershell.exe");
        ProcessBuilder builder = new ProcessBuilder(
            powershellExe,
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            script.toString(),
            "-Version",
            version,
            "-AdtPluginsDir",
            adtPluginsDir
        );
        if (force) {
            builder.command().add("-Force");
        }
        applyTrustedPath(builder);
        builder.inheritIO();
        builder.environment().putIfAbsent("OPENADT_HOME", home.toString());
        Process process = builder.start();
        if (!process.waitFor(30, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            CliLog.error("Runtime prepare timed out.");
            return 1;
        }
        return process.exitValue();
    }

    public static String readInstalledVersion() throws IOException {
        String fromJar = readVersionFromRunningJar();
        if (fromJar != null && !fromJar.isBlank()) {
            return normalizeReleaseVersion(fromJar);
        }
        Path marker = Path.of(System.getProperty(USER_HOME_PROPERTY), ".openadt/runtime/version.txt");
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
        Path jar = Path.of(System.getProperty(USER_HOME_PROPERTY), ".openadt/runtime/openadt-full.jar");
        if (!Files.isRegularFile(jar)) {
            return false;
        }
        Path marker = Path.of(System.getProperty(USER_HOME_PROPERTY), ".openadt/runtime/version.txt");
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

    private static Path openAdtHome() throws IOException {
        String env = System.getenv("OPENADT_HOME");
        if (env != null && !env.isBlank()) {
            return Path.of(env);
        }
        URI codeSource = URI.create(SetupRuntimePreparer.class.getProtectionDomain()
            .getCodeSource()
            .getLocation()
            .toString());
        Path jarOrClasses = Path.of(codeSource);
        Path parent = Files.isRegularFile(jarOrClasses) ? jarOrClasses.getParent() : jarOrClasses;
        if (parent == null) {
            throw new IOException("Could not resolve OpenADT install directory.");
        }
        return parent;
    }

    private static void applyTrustedPath(ProcessBuilder builder) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String systemRoot = System.getenv("SystemRoot");
            if (systemRoot == null || systemRoot.isBlank()) {
                systemRoot = "C:\\Windows";
            }
            String trustedPath = systemRoot + "\\System32\\WindowsPowerShell\\v1.0;"
                + systemRoot + "\\System32;"
                + systemRoot;
            String inheritedPath = builder.environment().getOrDefault("PATH", System.getenv("PATH"));
            builder.environment().put(
                "PATH",
                inheritedPath == null || inheritedPath.isBlank()
                    ? trustedPath
                    : trustedPath + ";" + inheritedPath
            );
            return;
        }
        String inheritedPath = builder.environment().getOrDefault("PATH", System.getenv("PATH"));
        builder.environment().put(
            "PATH",
            inheritedPath == null || inheritedPath.isBlank()
                ? "/usr/bin:/bin"
                : "/usr/bin:/bin:" + inheritedPath
        );
    }

    private static String windowsSystemExecutable(String relativePath) {
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot == null || systemRoot.isBlank()) {
            systemRoot = "C:\\Windows";
        }
        return Path.of(systemRoot, relativePath.split("\\\\")).toString();
    }
}
