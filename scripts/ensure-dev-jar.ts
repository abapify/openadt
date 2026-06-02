/**
 * Ensures apps/openadt-cli/target has a packaged openadt-*.jar for dev classpath
 * (third-party deps). Module sources are loaded from target/classes via nx compile.
 */
import { spawnSync } from "node:child_process";
import { existsSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";

const repoRoot = join(import.meta.dir, "..");
const targetDir = join(repoRoot, "apps", "openadt-cli", "target");

function findDevJar(): string | undefined {
  if (!existsSync(targetDir)) {
    return undefined;
  }
  const jars = readdirSync(targetDir)
    .filter(
      (name) =>
        name.startsWith("openadt-") &&
        name.endsWith(".jar") &&
        !/original|sources|javadoc|shaded/i.test(name),
    )
    .map((name) => join(targetDir, name))
    .filter((path) => statSync(path).isFile())
    .sort((a, b) => statSync(b).mtimeMs - statSync(a).mtimeMs);
  return jars[0];
}

if (findDevJar()) {
  process.exit(0);
}

console.error(
  "openadt: no packaged jar in apps/openadt-cli/target — running nx package openadt-cli (once, then cached)…",
);
const result = spawnSync(
  "bunx",
  ["nx", "package", "openadt-cli", "--output-style=stream"],
  {
    cwd: repoRoot,
    stdio: "inherit",
    shell: process.platform === "win32",
    env: process.env,
  },
);

if ((result.status ?? 1) !== 0) {
  process.exit(result.status ?? 1);
}

if (!findDevJar()) {
  console.error(
    "openadt: package finished but no openadt-*.jar found in apps/openadt-cli/target",
  );
  process.exit(1);
}
