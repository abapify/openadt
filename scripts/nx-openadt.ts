/**
 * Dev entry for `nx run openadt-cli:run -- <openadt args>`.
 * - `--profile=sso` / `--profile=http` → slim fat jar (`java -jar`)
 * - `snc` or no `--profile` (destination default) → full ADT classpath like openadt-sdk.ps1
 */
import { spawnSync } from "node:child_process";
import { existsSync, readdirSync, statSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";
import {
  buildSdkClasspathEntries,
  hasMinimalSdkBundles,
  supplementFromP2,
  type SapBundleDir,
} from "./sdk-classpath.ts";

const repoRoot = join(import.meta.dir, "..");
const cliDir = join(repoRoot, "apps", "openadt-cli");
const targetDir = join(cliDir, "target");
const sapLibDir = join(targetDir, "sap-lib");
const mainClass = "org.openadt.cli.OpenAdtCommand";
const pathSep = process.platform === "win32" ? ";" : ":";

function findDevJar(): string {
  if (!existsSync(targetDir)) {
    console.error(
      "Missing apps/openadt-cli/target. Run: nx run openadt-cli:build",
    );
    process.exit(1);
  }
  const jars = readdirSync(targetDir)
    .filter(
      (name) =>
        name.startsWith("openadt-") &&
        name.endsWith(".jar") &&
        !/original|sources|javadoc/i.test(name),
    )
    .map((name) => join(targetDir, name))
    .filter((path) => statSync(path).isFile())
    .sort((a, b) => statSync(b).mtimeMs - statSync(a).mtimeMs);
  if (jars.length === 0) {
    console.error(
      "No openadt-*.jar in apps/openadt-cli/target. Run: nx run openadt-cli:build",
    );
    process.exit(1);
  }
  return jars[0]!;
}

function normalizeProfile(value: string | undefined): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed.toLowerCase() : undefined;
}

function parseProfile(args: string[]): string | undefined {
  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (arg === "--profile" && i + 1 < args.length) {
      return normalizeProfile(args[i + 1]);
    }
    if (arg.startsWith("--profile=")) {
      return normalizeProfile(arg.slice("--profile=".length));
    }
  }
  return undefined;
}

/** HTTP browser SSO / plain HTTP transport — fat jar is enough. */
function useFatJar(profile: string | undefined): boolean {
  if (profile === undefined) {
    return false;
  }
  return profile === "sso" || profile === "http";
}

function sapBundleDirs(): SapBundleDir[] {
  if (
    existsSync(sapLibDir) &&
    readdirSync(sapLibDir).some((n) => n.endsWith(".jar"))
  ) {
    return [{ path: sapLibDir, kind: "sap-lib" }];
  }
  const p2 = join(homedir(), ".p2", "pool", "plugins");
  if (existsSync(p2)) {
    return [{ path: p2, kind: "p2" }];
  }
  return [];
}

function buildSdkClasspath(jar: string): string {
  const sapDirs = sapBundleDirs();
  let entries = buildSdkClasspathEntries({
    classesDir: join(cliDir, "target", "classes"),
    appJar: jar,
    sapDirs,
  });
  const p2 = join(homedir(), ".p2", "pool", "plugins");
  if (sapDirs[0]?.kind === "sap-lib") {
    entries = supplementFromP2(entries, p2);
  }
  if (!hasMinimalSdkBundles(entries)) {
    console.error(
      "SDK/SNC profile needs SAP ADT bundles on the classpath.\n" +
        "  - Run: nx run openadt-cli:build (fills apps/openadt-cli/target/sap-lib)\n" +
        "  - Or install Eclipse ADT (fills ~/.p2/pool/plugins)\n" +
        "  - Or: .\\scripts\\openadt-sdk.ps1 fetch …\n" +
        "  - For HTTP SSO only: add --profile=sso",
    );
    process.exit(1);
  }
  return entries.join(pathSep);
}

const jar = findDevJar();
const args = process.argv.slice(2);
const profile =
  parseProfile(args) ?? normalizeProfile(process.env.OPENADT_PROFILE);

const javaArgs = useFatJar(profile)
  ? ["-jar", jar, ...args]
  : ["-cp", buildSdkClasspath(jar), mainClass, ...args];

const result = spawnSync("java", javaArgs, {
  stdio: "inherit",
  cwd: repoRoot,
  env: process.env,
});
process.exit(result.status ?? 1);
