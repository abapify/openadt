/**
 * Dev Java entry for `nx run openadt-cli:run` (invoked after cached `compile` + `ensure-dev-jar`).
 * - `--profile=sso` / `--profile=http` → slim fat jar (`java -jar`)
 * - `auth`, `discovery`, `fetch`, `proxy` (default SNC) → SDK classpath from each app's target/classes + fat jar deps
 */
import { spawnSync } from "node:child_process";
import { existsSync, readdirSync, statSync } from "node:fs";
import {
  mcpNeedsStdioPipe,
  runMcpLauncherInherited,
  runMcpLauncherPiped,
} from "./mcp-launcher-spawn.ts";
import { spawnJavaWithClasspath } from "./java-argfile.ts";
import { homedir } from "node:os";
import { join } from "node:path";
import { resolveOpenadtDevRoot } from "./resolve-openadt-dev-root.ts";
import { loadDevRuntimeJars } from "./dev-runtime-classpath.ts";
import {
  buildSdkClasspathEntries,
  hasMinimalSdkBundles,
  resolveSapBundleDirs,
  supplementFromP2,
} from "./sdk-classpath.ts";

const repoRoot = resolveOpenadtDevRoot();
const cliDir = join(repoRoot, "apps", "openadt-cli");
const configDir = join(repoRoot, "apps", "openadt-config");
const bootstrapDir = join(repoRoot, "apps", "openadt-bootstrap");
const sapAdtDir = join(repoRoot, "apps", "openadt-sap-adt");
const targetDir = join(cliDir, "target");
const sapLibDir = join(targetDir, "sap-lib");
const runtimeSapLibDir = join(homedir(), ".openadt", "runtime", "sap-lib");
const p2Dir = join(homedir(), ".p2", "pool", "plugins");
const mainClass = "org.openadt.cli.OpenAdtCommand";
const pathSep = process.platform === "win32" ? ";" : ":";

function findDevJar(): string {
  if (!existsSync(targetDir)) {
    console.error(
      "Missing apps/openadt-cli/target. Run: nx package openadt-cli",
    );
    process.exit(1);
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
  if (jars.length === 0) {
    console.error(
      "No openadt-*.jar in apps/openadt-cli/target. Run: nx package openadt-cli",
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

const VALUE_FLAGS = new Set([
  "profile",
  "config",
  "collection",
  "category",
  "format",
]);

function isValueFlag(name: string): boolean {
  return VALUE_FLAGS.has(name);
}

function shouldSkipFlagValue(
  arg: string,
  nextIndex: number,
  argsLength: number,
): boolean {
  const eq = arg.indexOf("=");
  if (eq > 0) {
    return false;
  }
  const name = arg.slice(2);
  return isValueFlag(name) && nextIndex < argsLength;
}

function shouldSkipShortFlagValue(
  nextIndex: number,
  argsLength: number,
): boolean {
  return nextIndex < argsLength;
}

function firstSubcommandIndex(args: string[]): number {
  for (let i = 0; i < args.length; i++) {
    const arg = args[i]!;
    if (arg === "--") {
      return i + 1;
    }
    if (arg.startsWith("--")) {
      if (
        shouldSkipFlagValue(arg, i + 1, args.length) &&
        !args[i + 1]!.startsWith("-")
      ) {
        i++;
      }
      continue;
    }
    if (arg === "-c" || arg === "-p") {
      if (shouldSkipShortFlagValue(i + 1, args.length)) {
        i++;
      }
      continue;
    }
    if (arg.startsWith("-")) {
      continue;
    }
    return i;
  }
  return -1;
}

function firstSubcommand(args: string[]): string | undefined {
  const i = firstSubcommandIndex(args);
  return i >= 0 ? args[i] : undefined;
}

/** HTTP browser SSO / plain HTTP transport — fat jar is enough. */
function useFatJar(profile: string | undefined, args: string[]): boolean {
  const subcommand = firstSubcommand(args);
  if (
    subcommand === "auth" ||
    subcommand === "discovery" ||
    subcommand === "transports"
  ) {
    return false;
  }
  if (profile === undefined) {
    return false;
  }
  return profile === "sso" || profile === "http";
}

function sapBundleDirs() {
  return resolveSapBundleDirs({
    runtimeSapLibDir,
    projectSapLibDir: sapLibDir,
    p2Dir,
  });
}

/** Compiled module output ahead of the packaged jar (jar may lag behind workspace sources). */
function devModuleClassDirs(): string[] {
  return [
    join(configDir, "target", "classes"),
    join(bootstrapDir, "target", "classes"),
    join(sapAdtDir, "target", "classes"),
    join(cliDir, "target", "classes"),
  ];
}

function buildSdkClasspath(jar: string): string {
  const sapDirs = sapBundleDirs();
  let entries = buildSdkClasspathEntries({
    classesDirs: devModuleClassDirs(),
    runtimeJars: loadDevRuntimeJars(),
    appJar: jar,
    sapDirs,
  });
  if (sapDirs[0]?.kind === "sap-lib") {
    entries = supplementFromP2(entries, p2Dir);
  }
  if (!hasMinimalSdkBundles(entries)) {
    console.error(
      "SDK/SNC profile needs SAP ADT bundles on the classpath.\n" +
        "  - Run: nx package openadt-cli (fills apps/openadt-cli/target/sap-lib)\n" +
        "  - Or install Eclipse ADT (fills ~/.p2/pool/plugins)\n" +
        "  - Or: .\\scripts\\openadt-sdk.ps1 fetch …\n" +
        "  - From clone: ./dev-openadt fetch …\n" +
        "  - For HTTP SSO only: add --profile=sso",
    );
    process.exit(1);
  }
  return entries.join(pathSep);
}

const args = process.argv.slice(2);

const mcpIndex = firstSubcommandIndex(args);
if (mcpIndex >= 0 && args[mcpIndex] === "mcp") {
  const mcpArgs = args.slice(mcpIndex + 1);
  const resolved = mcpArgs.length > 0 ? mcpArgs : ["--help"];
  if (mcpNeedsStdioPipe(resolved)) {
    process.exit(await runMcpLauncherPiped(repoRoot, resolved));
  }
  process.exit(runMcpLauncherInherited(repoRoot, resolved));
}

const jar = findDevJar();

const profile =
  parseProfile(args) ?? normalizeProfile(process.env.OPENADT_PROFILE);

if (useFatJar(profile, args)) {
  const result = spawnSync("java", ["-jar", jar, ...args], {
    stdio: "inherit",
    cwd: repoRoot,
    env: process.env,
  });
  process.exit(result.status ?? 1);
}

process.exit(
  spawnJavaWithClasspath({
    classpath: buildSdkClasspath(jar),
    mainClass,
    args,
    cwd: repoRoot,
    env: process.env,
  }),
);
