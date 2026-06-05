import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";
import type { AdtLsInstall } from "./types.ts";

const EXTENSION_GLOB_PREFIX = "sapse.adt-vscode-";

type PlatformPaths = {
  adtLscRelative: string[];
};

function platformPaths(): PlatformPaths {
  const arch = process.arch === "arm64" ? "aarch64" : "x86_64";
  switch (process.platform) {
    case "win32":
      return {
        adtLscRelative: ["adt-ls", "win32", "win32", arch, "adt-lsc.exe"],
      };
    case "linux":
      return {
        adtLscRelative: ["adt-ls", "linux", "gtk", "linux", arch, "adt-lsc"],
      };
    case "darwin":
      return {
        adtLscRelative: [
          "adt-ls",
          "macosx",
          "cocoa",
          arch,
          "Adt-ls.app",
          "Contents",
          "MacOS",
          "adt-lsc",
        ],
      };
    default:
      throw new Error(`Unsupported platform: ${process.platform}`);
  }
}

/** Path segments to climb from the adt-lsc binary to the extension root. */
function adtLscParentSegments(): string[] {
  return Array(platformPaths().adtLscRelative.length).fill("..");
}

function extensionSearchRoots(): string[] {
  const home = homedir();
  return [
    join(home, ".vscode", "extensions"),
    join(home, ".cursor", "extensions"),
    join(home, ".vscode-insiders", "extensions"),
  ];
}

function parseVersionFromDirName(dirName: string): string {
  const match = /^sapse\.adt-vscode-(.+)$/.exec(dirName);
  return match?.[1] ?? dirName;
}

function readPackageVersion(extensionRoot: string): string | undefined {
  try {
    const pkg = JSON.parse(
      readFileSync(join(extensionRoot, "package.json"), "utf8"),
    ) as { version?: string };
    return pkg.version;
  } catch {
    return undefined;
  }
}

function listExtensionDirs(extensionsDir: string): string[] {
  if (!existsSync(extensionsDir)) {
    return [];
  }
  return readdirSync(extensionsDir)
    .filter((name) => name.startsWith(EXTENSION_GLOB_PREFIX))
    .map((name) => join(extensionsDir, name))
    .filter((path) => {
      try {
        return statSync(path).isDirectory();
      } catch {
        return false;
      }
    });
}

/** Pick newest extension install by folder suffix / package.json version. */
export function pickNewestExtension(
  candidates: { path: string; version: string }[],
): { path: string; version: string } | undefined {
  if (candidates.length === 0) {
    return undefined;
  }
  return [...candidates].sort((a, b) =>
    b.version.localeCompare(a.version, undefined, { numeric: true }),
  )[0];
}

export function findExtensionRoots(): { path: string; version: string }[] {
  const found: { path: string; version: string }[] = [];
  for (const root of extensionSearchRoots()) {
    for (const dir of listExtensionDirs(root)) {
      const folderVersion = parseVersionFromDirName(
        dir.split(/[/\\]/).pop() ?? dir,
      );
      const pkgVersion = readPackageVersion(dir);
      found.push({
        path: dir,
        version: pkgVersion ?? folderVersion,
      });
    }
  }
  return found;
}

export function resolveAdtLscFromExtension(
  extensionRoot: string,
): string | undefined {
  const rel = platformPaths().adtLscRelative;
  const adtLscPath = join(extensionRoot, ...rel);
  return existsSync(adtLscPath) ? adtLscPath : undefined;
}

export function locateAdtLs(): AdtLsInstall | undefined {
  const override = process.env.ADT_LS_PATH?.trim();
  if (override) {
    const adtLscPath = override;
    if (!existsSync(adtLscPath)) {
      return undefined;
    }
    const extensionRoot = join(adtLscPath, ...adtLscParentSegments());
    return {
      extensionRoot,
      adtLscPath,
      adtLsRoot: join(extensionRoot, "adt-ls"),
      version: "ADT_LS_PATH",
    };
  }

  const newest = pickNewestExtension(findExtensionRoots());
  if (!newest) {
    return undefined;
  }

  const adtLscPath = resolveAdtLscFromExtension(newest.path);
  if (!adtLscPath) {
    return undefined;
  }

  return {
    extensionRoot: newest.path,
    adtLscPath,
    adtLsRoot: join(newest.path, "adt-ls"),
    version: newest.version,
  };
}
