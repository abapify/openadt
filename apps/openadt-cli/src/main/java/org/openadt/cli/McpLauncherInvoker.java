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
    private static final String LAUNCHER_REL = "tools/sap-adt-mcp-launcher/src/main.ts";

    private McpLauncherInvoker() {}

    static int invoke(String subcommand, String[] extraArgs) {
        Path script = resolveLauncherMain();
        if (script == null) {
            CliLog.error(
                "SAP ADT MCP launcher not found at " + LAUNCHER_REL + ".\n"
                    + "Set OPENADT_REPO to your git clone, or run from the repository root.\n"
                    + "Dev entry: bun run openadt -- mcp .");
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
        String repo = System.getenv("OPENADT_REPO");
        if (repo != null && !repo.isBlank()) {
            Path p = Path.of(repo.trim(), LAUNCHER_REL);
            if (Files.isRegularFile(p)) {
                return p.toAbsolutePath().normalize();
            }
        }
        String home = System.getenv("OPENADT_HOME");
        if (home != null && !home.isBlank()) {
            Path p = Path.of(home.trim(), LAUNCHER_REL);
            if (Files.isRegularFile(p)) {
                return p.toAbsolutePath().normalize();
            }
        }
        Path cwd = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 8 && cwd != null; depth++) {
            Path candidate = cwd.resolve(LAUNCHER_REL);
            if (Files.isRegularFile(candidate)) {
                return candidate.normalize();
            }
            cwd = cwd.getParent();
        }
        return null;
    }

    private static void applyRepoEnv(ProcessBuilder pb, Path launcherMain) {
        Path repoRoot = launcherMain.getParent().getParent().getParent().getParent();
        if (Files.isDirectory(repoRoot)) {
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
