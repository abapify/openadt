import AdmZip from "adm-zip";
import { createHash } from "node:crypto";
import {
  cpSync,
  existsSync,
  mkdirSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { dirname, join, resolve } from "node:path";
import { spawnSync } from "node:child_process";

const root = resolve(import.meta.dir, "../../..");
const cliDir = join(root, "apps/openadt-cli");
const pomContent = readFileSync(join(root, "pom.xml"), "utf8");

const versionArg = process.argv
  .find((a) => a.startsWith("--version="))
  ?.split("=")[1];
const version = versionArg ?? process.env.OPENADT_VERSION ?? readPomVersion();

function readPomVersion(): string {
  const match =
    /<artifactId>openadt-parent<\/artifactId>\s+<version>([^<]+)<\/version>/.exec(
      pomContent,
    );
  if (!match) {
    throw new Error("Could not read version from pom.xml");
  }
  return match[1].trim().replace(/-SNAPSHOT$/, "");
}

const jarFile = (() => {
  const plain = join(cliDir, "target", `openadt-${version}.jar`);
  if (existsSync(plain)) {
    return plain;
  }
  const snapshot = join(cliDir, "target", `openadt-${version}-SNAPSHOT.jar`);
  if (existsSync(snapshot)) {
    return snapshot;
  }
  throw new Error(
    `Neither openadt-${version}.jar nor openadt-${version}-SNAPSHOT.jar found under ${join(cliDir, "target")}. Run 'mvn package' first.`,
  );
})();
const jarPath = jarFile;
const distDir = join(root, "packaging/dist");
const stageDir = join(distDir, `openadt-${version}`);
const zipName = `openadt-${version}.zip`;
const zipPath = join(distDir, zipName);

function sha256File(path: string): string {
  return createHash("sha256")
    .update(readFileSync(path))
    .digest("hex")
    .toUpperCase();
}

function buildWindowsExe(target: string): void {
  const launcherDir = join(root, "packaging/windows/launcher");
  const go = spawnSync("go", ["build", "-o", target, "."], {
    cwd: launcherDir,
    stdio: "pipe",
  });
  if (go.status === 0) {
    return;
  }
  if (go.stderr) {
    const stderr = go.stderr.toString().trim();
    if (stderr.length > 0) {
      console.error(stderr);
    }
  }

  const dotnet = spawnSync(
    "dotnet",
    [
      "publish",
      join(root, "packaging/windows/launcher/OpenAdtLauncher.csproj"),
      "-c",
      "Release",
      "-o",
      dirname(target),
      "/p:AssemblyName=openadt",
    ],
    { stdio: "inherit" },
  );
  if (dotnet.status !== 0) {
    throw new Error(
      "Failed to build openadt.exe. Install Go or .NET SDK (dotnet) on PATH.",
    );
  }
}

function writeLaunchers(base: string): void {
  mkdirSync(join(base, "bin"), { recursive: true });

  cpSync(
    join(root, "packaging/windows/openadt-launcher.ps1"),
    join(base, "bin/openadt-launcher.ps1"),
  );
  cpSync(
    join(root, "packaging/windows/prepare-openadt-runtime.ps1"),
    join(base, "bin/prepare-openadt-runtime.ps1"),
  );
  cpSync(
    join(root, "packaging/scoop/post-install.ps1"),
    join(base, "bin/scoop-post-install.ps1"),
  );

  writeFileSync(
    join(base, "bin/openadt.cmd"),
    `@echo off\r\nsetlocal EnableDelayedExpansion\r\nset "OPENADT_HOME=%~dp0.."\r\nset "OPENADT_ARG_COUNT=0"\r\n:openadt_args\r\nif "%~1"=="" goto openadt_run\r\nset "OPENADT_ARG_!OPENADT_ARG_COUNT!=%~1"\r\nset /a OPENADT_ARG_COUNT+=1\r\nshift\r\ngoto openadt_args\r\n:openadt_run\r\npowershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0openadt-launcher.ps1"\r\nexit /b %ERRORLEVEL%\r\n`,
  );

  writeFileSync(
    join(base, "bin/openadt.ps1"),
    `param(
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]] $OpenAdtArgs
)
$env:OPENADT_HOME = Split-Path -Parent $PSScriptRoot
& (Join-Path $PSScriptRoot 'openadt-launcher.ps1') @OpenAdtArgs
exit $LASTEXITCODE
`,
  );

  writeFileSync(
    join(base, "bin/openadt"),
    `#!/usr/bin/env bash
set -euo pipefail
OPENADT_HOME="$(cd "$(dirname "$0")/.." && pwd)"
exec java -jar "$OPENADT_HOME/openadt.jar" "$@"
`,
    { mode: 0o755 },
  );
}

const HOST_PLATFORM_MAP: Record<string, Record<string, string>> = {
  win32: { x64: "win-x64" },
  linux: { x64: "linux-x64" },
  darwin: { arm64: "darwin-arm64", x64: "darwin-x64" },
};

function currentMatrixPlatform(): string {
  if (process.env.OPENADT_MATRIX_PLATFORM) {
    return process.env.OPENADT_MATRIX_PLATFORM;
  }
  const platform = HOST_PLATFORM_MAP[process.platform];
  const match = platform?.[process.arch];
  if (match) {
    return match;
  }
  throw new Error(
    `Unsupported host platform ${process.platform}/${process.arch} for openadt-mcp packaging`,
  );
}

function packageMcpBinary(version: string): void {
  const platform = currentMatrixPlatform();
  const ext = platform.startsWith("win-") ? "zip" : "tar.gz";
  const stageDirName = `openadt-mcp-${version}-${platform}`;
  const stageDir = join(distDir, stageDirName);
  const archiveName = `${stageDirName}.${ext}`;
  const archivePath = join(distDir, archiveName);

  const build = spawnSync(
    "bun",
    [
      "run",
      "mcp:build:compile",
      "--",
      `--platform=${platform}`,
      `--out=${stageDir}`,
    ],
    { stdio: "inherit" },
  );
  if (build.error) {
    throw new Error(
      `Failed to spawn mcp:build:compile: ${build.error.message}`,
    );
  }
  if (build.status !== 0) {
    throw new Error(
      `mcp:build:compile for ${platform} exited with status ${build.status ?? "unknown"}`,
    );
  }

  if (platform.startsWith("win-")) {
    const zip = new AdmZip();
    zip.addLocalFolder(stageDir, stageDirName);
    zip.writeZip(archivePath);
  } else {
    const tar = spawnSync("tar", ["czf", archivePath, stageDirName], {
      cwd: distDir,
      stdio: "inherit",
    });
    if (tar.status !== 0) {
      throw new Error(
        `tar exited with status ${tar.status ?? "unknown"} while packaging ${archiveName}`,
      );
    }
  }

  const mcpSha = sha256File(archivePath);
  writeFileSync(`${archivePath}.sha256`, `${mcpSha}  ${archiveName}\n`);

  // Patch the openadt-mcp packaging files with the real sha256.
  const formulaPath = join(root, "packaging/homebrew/openadt-mcp.rb");
  let ruby = readFileSync(formulaPath, "utf8");
  ruby = ruby.replace(/sha256 "[^"]+"/, `sha256 "${mcpSha.toLowerCase()}"`);
  writeFileSync(formulaPath, ruby);
  syncHomebrewTapFormula(formulaPath, "openadt-mcp");

  const manifestPath = join(root, "packaging/scoop/openadt-mcp.json");
  const manifest = JSON.parse(readFileSync(manifestPath, "utf8")) as {
    version: string;
    extract_dir: string;
    architecture: { "64bit": { url: string; hash: string } };
  };
  manifest.version = version;
  manifest.extract_dir = stageDirName;
  manifest.architecture["64bit"].url =
    `https://github.com/abapify/openadt/releases/download/v${version}/${archiveName}`;
  manifest.architecture["64bit"].hash = mcpSha.toLowerCase();
  writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 4)}\n`);

  console.log(`Packaged ${archivePath}`);
  console.log(`SHA256 ${mcpSha}`);
}

function updateHomebrewSha256(sha256: string): void {
  const formulaPath = join(root, "packaging/homebrew/openadt.rb");
  let ruby = readFileSync(formulaPath, "utf8");
  ruby = ruby.replace(/sha256 "[^"]+"/, `sha256 "${sha256.toLowerCase()}"`);
  writeFileSync(formulaPath, ruby);
  syncHomebrewTapFormula(formulaPath, "openadt");
}

function syncHomebrewTapFormula(formulaPath: string, product: string): void {
  const tapPath = join(root, `Formula/${product}.rb`);
  mkdirSync(dirname(tapPath), { recursive: true });
  cpSync(formulaPath, tapPath);
}

function updateScoopSha256(sha256: string): void {
  const manifestPath = join(root, "packaging/scoop/openadt.json");
  const manifest = JSON.parse(readFileSync(manifestPath, "utf8")) as {
    architecture: { "64bit": { url: string; hash: string } };
  };
  manifest.architecture["64bit"].url =
    `https://github.com/abapify/openadt/releases/download/v${version}/${zipName}`;
  manifest.architecture["64bit"].hash = sha256.toLowerCase();
  writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 4)}\n`);
}

rmSync(stageDir, { recursive: true, force: true });
mkdirSync(stageDir, { recursive: true });

if (!existsSync(jarPath)) {
  throw new Error(
    `Missing jar. Run: cd apps/openadt-cli && mvnw -Pdistribution -Dopenadt.distribution=true package -DskipTests`,
  );
}

cpSync(jarPath, join(stageDir, "openadt.jar"));
writeFileSync(join(stageDir, "VERSION"), `${version}\n`);
cpSync(join(root, "LICENSE"), join(stageDir, "LICENSE"));

writeLaunchers(stageDir);
if (
  process.platform === "win32" ||
  process.env.OPENADT_PACKAGE_WIN_EXE === "1"
) {
  buildWindowsExe(join(stageDir, "openadt.exe"));
  for (const extra of [
    "openadt.pdb",
    "openadt.deps.json",
    "openadt.runtimeconfig.json",
  ]) {
    const path = join(stageDir, extra);
    if (existsSync(path)) {
      rmSync(path);
    }
  }
}

const zip = new AdmZip();
zip.addLocalFolder(stageDir, `openadt-${version}`);
zip.writeZip(zipPath);

const sha256 = sha256File(zipPath);
writeFileSync(`${zipPath}.sha256`, `${sha256}  ${zipName}\n`);
updateHomebrewSha256(sha256);
updateScoopSha256(sha256);

console.log(`Packaged ${zipPath}`);
console.log(`SHA256 ${sha256}`);

packageMcpBinary(version);
