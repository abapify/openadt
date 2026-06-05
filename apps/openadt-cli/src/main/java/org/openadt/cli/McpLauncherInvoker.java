package org.openadt.cli;

import org.openadt.config.CliLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Delegates {@code openadt mcp *} to the Bun SAP ADT MCP launcher. */
final class McpLauncherInvoker {
    private static final String[] LAUNCHER_REL_PATHS = {
        "sap-adt-mcp-launcher/src/main.ts",
        "tools/sap-adt-mcp-launcher/src/main.ts",
    };

    private McpLauncherInvoker() {}

    static int invoke(String subcommand, String[] extraArgs) {
        Path script = resolveLauncherMain();
        if (script == null) {
            CliLog.error(
                "SAP ADT MCP launcher not found under OPENADT_HOME or OPENADT_REPO.\n"
                    + "Reinstall OpenADT or set OPENADT_REPO to your git clone.\n"
                    + "Requires Bun on PATH: https://bun.sh");
            return 1;
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(resolveBunExecutable());
        cmd.add(script.toString());
        cmd.add(subcommand);
        if (extraArgs != null) {
            for (String arg : extraArgs) {
                cmd.add(arg);
            }
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        applyRepoEnv(pb, script);
        try {
            Process process = pb.start();
            return process.waitFor();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            CliLog.error("Failed to run MCP launcher: " + e.getMessage());
            return 1;
        }
    }

    static Path resolveLauncherMain() {
        List<String> bases = new ArrayList<>();
        String home = System.getenv("OPENADT_HOME");
        if (home != null && !home.isBlank()) {
            bases.add(home.trim());
        }
        String repo = System.getenv("OPENADT_REPO");
        if (repo != null && !repo.isBlank()) {
            bases.add(repo.trim());
        }
        for (String base : bases) {
            Path root = Path.of(base);
            for (String rel : LAUNCHER_REL_PATHS) {
                Path candidate = root.resolve(rel);
                if (Files.isRegularFile(candidate)) {
                    return candidate.toAbsolutePath().normalize();
                }
            }
        }
        Path cwd = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 8 && cwd != null; depth++) {
            for (String rel : LAUNCHER_REL_PATHS) {
                Path candidate = cwd.resolve(rel);
                if (Files.isRegularFile(candidate)) {
                    return candidate.normalize();
                }
            }
            cwd = cwd.getParent();
        }
        return null;
    }

    private static void applyRepoEnv(ProcessBuilder pb, Path launcherMain) {
        // Derive the repo root from the matched LAUNCHER_REL_PATH: the matched
        // rel path is anchored at the repo root, so we step up by its segment
        // count regardless of which of the two layouts (shallow `...` or
        // `tools/...`) is in use. This avoids hard-coding the depth per layout.
        String normalized = launcherMain.toString().replace('\\', '/');
        String matchedRel = null;
        for (String rel : LAUNCHER_REL_PATHS) {
            if (normalized.endsWith("/" + rel) || normalized.equals(rel)) {
                matchedRel = rel;
                break;
            }
        }
        Path repoRoot = launcherMain;
        if (matchedRel != null) {
            for (int i = 0; i < matchedRel.split("/").length; i++) {
                Path parent = repoRoot.getParent();
                if (parent == null) {
                    break;
                }
                repoRoot = parent;
            }
        }
        if (Files.isDirectory(repoRoot)) {
            pb.environment().putIfAbsent("OPENADT_HOME", repoRoot.toString());
            pb.environment().putIfAbsent("OPENADT_REPO", repoRoot.toString());
        }
    }

    private static String resolveBunExecutable() {
        String override = System.getenv("OPENADT_BUN");
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        if (isWindows()) {
            return "bun.exe";
        }
        return "bun";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
