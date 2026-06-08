#!/usr/bin/env bun
/**
 * Build the `openadt-mcp` standalone binary for one matrix platform.
 *
 * Usage:
 *   bun scripts/build-openadt-mcp-release.ts --platform=<platform> [--out=<dir>]
 *
 * Where <platform> is one of: win-x64, linux-x64, darwin-arm64, darwin-x64.
 *
 * Output layout under <dir>:
 *   openadt-mcp[.exe]      compiled Bun binary
 *   LICENSE                 copy of repo LICENSE
 *   README.md               copy of tools/sap-adt-mcp-launcher/README.md
 *   VERSION                 "<version>" (one line, trailing newline)
 *
 * Default <out> = packaging/dist/openadt-mcp-<version>-<platform>
 */
import {
  cpSync,
  existsSync,
  mkdirSync,
  readFileSync,
  statSync,
  writeFileSync,
} from "node:fs";
import { join, resolve } from "node:path";
import { spawnSync } from "node:child_process";

const PLATFORMS = new Set([
  "win-x64",
  "linux-x64",
  "darwin-arm64",
  "darwin-x64",
]);

export function parsePlatform(value: string | undefined): string {
  if (!value || !PLATFORMS.has(value)) {
    throw new Error(
      `Unknown --platform=${value ?? ""}; expected one of: ${[...PLATFORMS].join(", ")}`,
    );
  }
  return value;
}

function readVersion(root: string): string {
  const pom = readFileSync(join(root, "pom.xml"), "utf8");
  const match =
    /<artifactId>openadt-parent<\/artifactId>\s+<version>([^<]+)<\/version>/.exec(
      pom,
    );
  if (!match) {
    throw new Error("Could not read version from pom.xml");
  }
  return match[1].trim().replace(/-SNAPSHOT$/, "");
}

function spawnOrThrow(cmd: string, args: string[]): void {
  const r = spawnSync(cmd, args, { stdio: "inherit" });
  if (r.error) {
    throw new Error(`Failed to spawn ${cmd}: ${r.error.message}`);
  }
  if (r.status !== 0) {
    throw new Error(
      `${cmd} ${args.join(" ")} exited with status ${r.status ?? "unknown"}`,
    );
  }
}

function main(): void {
  const root = resolve(import.meta.dir, "..");
  const platformArg = process.argv
    .find((a) => a.startsWith("--platform="))
    ?.split("=")[1];
  const outArg = process.argv
    .find((a) => a.startsWith("--out="))
    ?.split("=")[1];
  const platform = parsePlatform(platformArg);
  const version = readVersion(root);
  const outDir =
    outArg ??
    join(root, "packaging/dist", `openadt-mcp-${version}-${platform}`);

  mkdirSync(outDir, { recursive: true });
  const ext = platform.startsWith("win-") ? ".exe" : "";
  const entry = join(root, "tools/sap-adt-mcp-launcher/src/openadt-mcp-bin.ts");
  const outfile = join(outDir, `openadt-mcp${ext}`);

  spawnOrThrow("bun", [
    "build",
    "--compile",
    "--minify",
    entry,
    "--outfile",
    outfile,
  ]);

  cpSync(join(root, "LICENSE"), join(outDir, "LICENSE"));
  cpSync(
    join(root, "tools/sap-adt-mcp-launcher/README.md"),
    join(outDir, "README.md"),
  );
  const postInstallSrc = join(
    root,
    "packaging/scoop/openadt-mcp-post-install.ps1",
  );
  if (existsSync(postInstallSrc)) {
    mkdirSync(join(outDir, "bin"), { recursive: true });
    cpSync(postInstallSrc, join(outDir, "bin", "openadt-mcp-post-install.ps1"));
  }
  writeFileSync(join(outDir, "VERSION"), `${version}\n`);

  const size = statSync(outfile).size;
  console.log(`Built ${outfile} (${size} bytes) for ${platform}`);
}

if (import.meta.main) {
  try {
    main();
  } catch (err) {
    console.error((err as Error).message);
    process.exit(1);
  }
}
