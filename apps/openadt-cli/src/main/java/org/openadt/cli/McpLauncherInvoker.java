package org.openadt.cli;

import org.openadt.config.CliLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Delegates {@code openadt mcp *} to the Bun SAP ADT MCP launcher. */
final class McpLauncherInvoker {
    private static final String[] LAUNCHER_REL_PATHS = {
        "sap-adt-mcp-launcher/src/main.ts",
        "tools/sap-adt-mcp-launcher/src/main.ts",
    };
    private static final int CWD_WALK_MAX_DEPTH = 8;

    private McpLauncherInvoker() {}

    static int invoke(String subcommand, String[] extraArgs) {
        Path script = resolveLauncherMain();
        if (script == null) {
            CliLog.error("""
                    SAP ADT MCP launcher not found under OPENADT_HOME or OPENADT_REPO.
                    Reinstall OpenADT or set OPENADT_REPO to your git clone.
                    Requires Bun on PATH: https://bun.sh""");
            return 1;
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(resolveBunExecutable());
        cmd.add(script.toString());
        cmd.add(subcommand);
        if (extraArgs != null && extraArgs.length > 0) {
            cmd.addAll(Arrays.asList(extraArgs));
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        applyRepoEnv(pb, script);
        try {
            Process process = pb.start();
            return process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            CliLog.error("Failed to run MCP launcher: " + e.getMessage());
            return 1;
        } catch (IOException e) {
            CliLog.error("Failed to run MCP launcher: " + e.getMessage());
            return 1;
        }
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
