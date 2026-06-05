/**
 * Writes Maven compile classpath for openadt-cli (-am) for ./dev-openadt dev runs.
 */
import { spawnSync } from "node:child_process";
import { existsSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";

const repoRoot = join(import.meta.dir, "..");
const cliDir = join(repoRoot, "apps", "openadt-cli");
const cpFile = join(cliDir, "target", "dev-runtime-classpath.txt");
const pathSep = process.platform === "win32" ? ";" : ":";

export function ensureDevRuntimeClasspath(): void {
  if (existsSync(cpFile) && statSync(cpFile).size > 50) {
    return;
  }
  const output = cpFile.replace(/\\/g, "/");
  const result = spawnSync(
    "node",
    [
      join(repoRoot, "scripts", "mvnw-runner.mjs"),
      "-q",
      "-pl",
      "apps/openadt-cli",
      "-am",
      "dependency:build-classpath",
      `-Dmdep.pathSeparator=${pathSep}`,
      `-Dmdep.outputFile=${output}`,
      "-DincludeScope=compile",
      "-f",
      join(repoRoot, "pom.xml"),
    ],
    { cwd: repoRoot, stdio: "inherit", env: process.env },
  );
  if ((result.status ?? 1) !== 0) {
    process.exit(result.status ?? 1);
  }
}

export function loadDevRuntimeJars(): string[] {
  ensureDevRuntimeClasspath();
  if (!existsSync(cpFile)) {
    return [];
  }
  return readFileSync(cpFile, "utf8")
    .split(pathSep)
    .map((entry) => entry.trim())
    .filter(
      (entry) =>
        entry.length > 0 &&
        existsSync(entry) &&
        !/junit|opentest4j|apiguardian/i.test(entry),
    );
}

if (import.meta.main) {
  ensureDevRuntimeClasspath();
}
