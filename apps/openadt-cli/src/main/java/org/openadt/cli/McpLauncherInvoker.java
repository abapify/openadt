package org.openadt.cli;

import org.openadt.config.CliLog;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Delegates {@code openadt mcp *} to the standalone {@code openadt-mcp} binary
 *  (fast path) or, in a dev clone, to the Bun SAP ADT MCP launcher (fallback). */
final class McpLauncherInvoker {
    private static final String[] LAUNCHER_REL_PATHS = {
        "sap-adt-mcp-launcher/dist/main.mjs",
        "sap-adt-mcp-launcher/dist/main.js",
        "sap-adt-mcp-launcher/src/main.ts",
        "tools/sap-adt-mcp-launcher/dist/main.mjs",
        "tools/sap-adt-mcp-launcher/dist/main.js",
        "tools/sap-adt-mcp-launcher/src/main.ts",
    };
    private static final int CWD_WALK_MAX_DEPTH = 8;

    private McpLauncherInvoker() {}

    static int invoke(String subcommand, String[] extraArgs) {
        Path binary = resolveOpenAdtMcpBinary();
        if (binary != null) {
            return spawnDirect(binary, subcommand, extraArgs);
        }
        Path script = resolveLauncherMain();
        if (script != null) {
            return spawnBun(script, subcommand, extraArgs);
        }
        CliLog.error("""
                openadt-mcp is not installed and no dev clone was found.
                Install: scoop install openadt-mcp
                       brew install openadt-mcp
                Or set OPENADT_REPO to your git clone (and have Bun on PATH).""");
        return 1;
    }

    private static int spawnDirect(Path binary, String subcommand, String[] extraArgs) {
        ProcessBuilder pb = new ProcessBuilder(buildArgv(binary, subcommand, extraArgs, null));
        pb.inheritIO();
        try {
            return pb.start().waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            CliLog.error("Failed to run openadt-mcp: " + e.getMessage());
            return 1;
        } catch (IOException e) {
            CliLog.error("Failed to run openadt-mcp: " + e.getMessage());
            return 1;
        }
    }

    private static int spawnBun(Path script, String subcommand, String[] extraArgs) {
        ProcessBuilder pb = new ProcessBuilder(
                buildArgv(script, subcommand, extraArgs, resolveBunExecutable()));
        pb.inheritIO();
        applyRepoEnv(pb, script);
        try {
            return pb.start().waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            CliLog.error("Failed to run MCP launcher: " + e.getMessage());
            return 1;
        } catch (IOException e) {
            CliLog.error("Failed to run MCP launcher: " + e.getMessage());
            return 1;
        }
    }

    private static List<String> buildArgv(
            Path executable, String subcommand, String[] extraArgs, String prefix) {
        List<String> cmd = new ArrayList<>();
        if (prefix != null) {
            cmd.add(prefix);
        }
        cmd.add(executable.toString());
        cmd.add(subcommand);
        if (extraArgs != null && extraArgs.length > 0) {
            cmd.addAll(Arrays.asList(extraArgs));
        }
        return cmd;
    }

    static Path resolveOpenAdtMcpBinary() {
        String override = System.getenv("OPENADT_MCP");
        if (override != null && !override.isBlank()) {
            Path envHit = Path.of(override.trim());
            if (Files.isRegularFile(envHit)) {
                return envHit.toAbsolutePath().normalize();
            }
            return null;
        }
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return null;
        }
        String exeName = isWindows() ? "openadt-mcp.exe" : "openadt-mcp";
        for (String dir : pathEnv.split(File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            Path candidate = Path.of(dir).resolve(exeName);
            if (Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        return null;
    }

    static Path resolveLauncherMain() {
        Path envHit = findInEnvBases();
        if (envHit != null) {
            return envHit;
        }
        return findFromCwd();
    }

    private static Path findInEnvBases() {
        for (String base : envBases()) {
            Path hit = findInRoot(Path.of(base), true);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private static Path findFromCwd() {
        Path cwd = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < CWD_WALK_MAX_DEPTH && cwd != null; depth++) {
            Path hit = findInRoot(cwd, false);
            if (hit != null) {
                return hit;
            }
            cwd = cwd.getParent();
        }
        return null;
    }

    private static Path findInRoot(Path root, boolean absolute) {
        for (String rel : LAUNCHER_REL_PATHS) {
            Path candidate = root.resolve(rel);
            if (Files.isRegularFile(candidate)) {
                return absolute
                        ? candidate.toAbsolutePath().normalize()
                        : candidate.normalize();
            }
        }
        return null;
    }

    private static List<String> envBases() {
        List<String> bases = new ArrayList<>();
        String home = System.getenv("OPENADT_HOME");
        if (home != null && !home.isBlank()) {
            bases.add(home.trim());
        }
        String repo = System.getenv("OPENADT_REPO");
        if (repo != null && !repo.isBlank()) {
            bases.add(repo.trim());
        }
        return bases;
    }

    private static void applyRepoEnv(ProcessBuilder pb, Path launcherMain) {
        // Derive the repo root from the matched LAUNCHER_REL_PATH: the matched
        // rel path is anchored at the repo root, so we step up by its segment
        // count regardless of which of the two layouts (shallow `...` or
        // `tools/...`) is in use. This avoids hard-coding the depth per layout.
        String rel = matchedRelPath(launcherMain);
        Path repoRoot =
                rel == null ? launcherMain : walkUpSegments(launcherMain, rel.split("/").length);
        if (!Files.isDirectory(repoRoot)) {
            return;
        }
        String root = repoRoot.toString();
        pb.environment().putIfAbsent("OPENADT_HOME", root);
        pb.environment().putIfAbsent("OPENADT_REPO", root);
    }

    private static String matchedRelPath(Path launcherMain) {
        String normalized = launcherMain.toString().replace('\\', '/');
        for (String rel : LAUNCHER_REL_PATHS) {
            if (normalized.endsWith("/" + rel) || normalized.equals(rel)) {
                return rel;
            }
        }
        return null;
    }

    private static Path walkUpSegments(Path start, int segments) {
        Path current = start;
        for (int i = 0; i < segments; i++) {
            Path parent = current.getParent();
            if (parent == null) {
                return current;
            }
            current = parent;
        }
        return current;
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
