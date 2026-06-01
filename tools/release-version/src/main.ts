import {
  existsSync,
  mkdirSync,
  readdirSync,
  readFileSync,
  writeFileSync,
} from "node:fs";
import { join, resolve } from "node:path";
import { spawnSync } from "node:child_process";

const root = resolve(import.meta.dir, "../../..");
const bumpArg = process.argv
  .find((a) => a.startsWith("--bump="))
  ?.split("=")[1];
const prereleaseIdArg = process.argv.find((a) =>
  a.startsWith("--prerelease-id="),
);
const prereleaseId = prereleaseIdArg
  ? prereleaseIdArg.slice("--prerelease-id=".length).trim()
  : "";

const PRE_BUMPS = new Set(["prerelease", "prepatch", "preminor", "premajor"]);
const ALLOWED_PRE_IDS = new Set(["rc", "beta", "alpha"]);

if (!bumpArg) {
  console.error(
    "Usage: bun tools/release-version/src/main.ts --bump=patch|minor|major|...",
  );
  process.exit(1);
}

type SemVer = {
  major: number;
  minor: number;
  patch: number;
  prerelease: string[];
};

const BUMP_TYPES = new Set([
  "major",
  "minor",
  "patch",
  "prerelease",
  "premajor",
  "preminor",
  "prepatch",
]);

if (!BUMP_TYPES.has(bumpArg)) {
  console.error(`Unknown bump type: ${bumpArg}`);
  process.exit(1);
}

function parseVersion(raw: string): SemVer {
  const cleaned = raw.trim().replace(/^v/i, "");
  const match = /^(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?$/.exec(cleaned);
  if (!match) {
    throw new Error(`Invalid semver: ${raw}`);
  }
  const prerelease = match[4] ? match[4].split(".") : [];
  return {
    major: Number(match[1]),
    minor: Number(match[2]),
    patch: Number(match[3]),
    prerelease,
  };
}

function formatVersion(v: SemVer): string {
  const core = `${v.major}.${v.minor}.${v.patch}`;
  return v.prerelease.length > 0 ? `${core}-${v.prerelease.join(".")}` : core;
}

function bumpVersion(current: SemVer, bump: string, preId: string): SemVer {
  const next: SemVer = {
    major: current.major,
    minor: current.minor,
    patch: current.patch,
    prerelease: [...current.prerelease],
  };
  const hasPre = next.prerelease.length > 0;

  switch (bump) {
    case "major":
      next.major += 1;
      next.minor = 0;
      next.patch = 0;
      next.prerelease = [];
      break;
    case "premajor":
      next.major += 1;
      next.minor = 0;
      next.patch = 0;
      next.prerelease = [preId, "1"];
      break;
    case "minor":
      next.minor += 1;
      next.patch = 0;
      next.prerelease = [];
      break;
    case "preminor":
      next.minor += 1;
      next.patch = 0;
      next.prerelease = [preId, "1"];
      break;
    case "patch":
      if (hasPre) {
        next.prerelease = [];
      } else {
        next.patch += 1;
        next.prerelease = [];
      }
      break;
    case "prepatch":
      next.patch += 1;
      next.prerelease = [preId, "1"];
      break;
    case "prerelease":
      if (!hasPre) {
        next.patch += 1;
        next.prerelease = [preId, "1"];
      } else {
        next.prerelease = incrementNumericPrerelease(next.prerelease);
      }
      break;
    default:
      throw new Error(`Unhandled bump: ${bump}`);
  }
  return next;
}

function incrementNumericPrerelease(parts: string[]): string[] {
  const copy = [...parts];
  for (let i = copy.length - 1; i >= 0; i -= 1) {
    if (/^\d+$/.test(copy[i])) {
      copy[i] = String(Number(copy[i]) + 1);
      return copy;
    }
  }
  copy.push("1");
  return copy;
}

function latestGitTag(): string | null {
  const result = spawnSync("git", ["tag", "-l", "v*", "--sort=-v:refname"], {
    cwd: root,
    encoding: "utf8",
  });
  if (result.status !== 0) {
    throw new Error(`git tag failed: ${result.stderr}`);
  }
  const tag = result.stdout
    .split(/\r?\n/)
    .find((line) => line.trim().length > 0);
  return tag?.trim() ?? null;
}

function readPomBaselineVersion(): SemVer {
  const pomPath = join(root, "pom.xml");
  const pom = readFileSync(pomPath, "utf8");
  const match =
    /<artifactId>openadt-parent<\/artifactId>\s*\n\s*<version>([^<]+)<\/version>/.exec(
      pom,
    );
  if (!match) {
    throw new Error(`Could not read version from ${pomPath}`);
  }
  return parseVersion(match[1].trim().replace(/-SNAPSHOT$/, ""));
}

const PARENT_COORD_MARKER = "<artifactId>openadt-parent</artifactId>";

/** Updates only the parent coordinate version (never plugin or dependency versions). */
function writeOpenAdtParentVersion(pomPath: string, version: string): void {
  const pom = readFileSync(pomPath, "utf8");
  const markerIndex = pom.indexOf(PARENT_COORD_MARKER);
  if (markerIndex < 0) {
    throw new Error(`Could not find openadt-parent in ${pomPath}`);
  }
  const openTag = pom.indexOf("<version>", markerIndex);
  const closeTag = pom.indexOf("</version>", openTag);
  if (openTag < 0 || closeTag < 0) {
    throw new Error(`Could not update parent version in ${pomPath}`);
  }
  const updated = `${pom.slice(0, openTag)}<version>${version}</version>${pom.slice(closeTag + "</version>".length)}`;
  writeFileSync(pomPath, updated);
}

function syncModuleParentVersions(version: string): void {
  const appsDir = join(root, "apps");
  if (!existsSync(appsDir)) {
    return;
  }
  for (const entry of readdirSync(appsDir, { withFileTypes: true })) {
    if (!entry.isDirectory()) {
      continue;
    }
    const pomPath = join(appsDir, entry.name, "pom.xml");
    if (!existsSync(pomPath)) {
      continue;
    }
    writeOpenAdtParentVersion(pomPath, version);
  }
}

/** v1.2.0 release tooling once wrote the product version into maven-dependency-plugin. */
function repairCliDependencyPluginVersion(): void {
  const pomPath = join(root, "apps/openadt-cli/pom.xml");
  if (!existsSync(pomPath)) {
    return;
  }
  let pom = readFileSync(pomPath, "utf8");
  const match =
    /<artifactId>maven-dependency-plugin<\/artifactId>\s*\n\s*<version>([^<]+)<\/version>/.exec(
      pom,
    );
  if (!match) {
    return;
  }
  const pluginVersion = match[1].trim();
  if (!/^1\.\d+\.\d+$/.test(pluginVersion)) {
    return;
  }
  pom = pom.replace(
    /(<artifactId>maven-dependency-plugin<\/artifactId>\s*\n\s*<version>)[^<]+(<\/version>)/,
    "$13.10.0$2",
  );
  writeFileSync(pomPath, pom);
  console.log(
    `Repaired maven-dependency-plugin ${pluginVersion} -> 3.10.0 in apps/openadt-cli/pom.xml`,
  );
}

function listWingetVersionDirs(): string[] {
  const base = join(root, "packaging/winget/manifests/o/OpenADT/OpenADT");
  if (!existsSync(base)) {
    return [];
  }
  return readdirSync(base, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .sort((a, b) => {
      try {
        return (
          parseVersion(b).major - parseVersion(a).major ||
          parseVersion(b).minor - parseVersion(a).minor ||
          parseVersion(b).patch - parseVersion(a).patch
        );
      } catch {
        return b.localeCompare(a);
      }
    });
}

function syncWingetManifests(version: string, templateVersion: string): void {
  const base = join(root, "packaging/winget/manifests/o/OpenADT/OpenADT");
  const templateDir = join(base, templateVersion);
  const targetDir = join(base, version);
  if (!existsSync(templateDir)) {
    throw new Error(`Winget template folder missing: ${templateDir}`);
  }
  mkdirSync(targetDir, { recursive: true });
  for (const file of readdirSync(templateDir)) {
    const from = join(templateDir, file);
    const to = join(targetDir, file.replaceAll(templateVersion, version));
    let content = readFileSync(from, "utf8");
    content = content.replaceAll(templateVersion, version);
    writeFileSync(to, content);
  }
}

function updateHomebrew(version: string): void {
  const formulaPath = join(root, "packaging/homebrew/openadt.rb");
  let formula = readFileSync(formulaPath, "utf8");
  formula = formula.replace(/STABLE = "[^"]+"/, `STABLE = "${version}"`);
  formula = formula.replace(
    /sha256 "[^"]+"/,
    'sha256 "PLACEHOLDER_RUN_PACKAGE_RELEASE"',
  );
  formula = formula.replace(
    /# Stable: prebuilt zip from GitHub Releases \(sha256 updated by `bun run package:release`\)\./,
    `# Stable: prebuilt zip from GitHub Releases (sha256 updated by package:release on v${version}).`,
  );
  writeFileSync(formulaPath, formula);
}

function updateScoop(version: string): void {
  const manifestPath = join(root, "packaging/scoop/openadt.json");
  const manifest = JSON.parse(readFileSync(manifestPath, "utf8")) as {
    version: string;
    extract_dir: string;
    architecture: { "64bit": { url: string; hash: string } };
  };
  manifest.version = version;
  manifest.extract_dir = `openadt-${version}`;
  manifest.architecture["64bit"].url =
    `https://github.com/abapify/openadt/releases/download/v${version}/openadt-${version}.zip`;
  manifest.architecture["64bit"].hash = "PLACEHOLDER_RUN_PACKAGE_RELEASE";
  writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 4)}\n`);
}

function writeGithubOutput(version: string): void {
  const output = process.env.GITHUB_OUTPUT;
  if (!output) {
    return;
  }
  writeFileSync(output, `version=${version}\ntag=v${version}\n`, { flag: "a" });
}

function requiresPrereleaseId(bump: string, current: SemVer): boolean {
  if (!PRE_BUMPS.has(bump)) {
    return false;
  }
  if (bump === "prerelease" && current.prerelease.length > 0) {
    return false;
  }
  return true;
}

const latestTag = latestGitTag();
const baseVersion = latestTag
  ? parseVersion(latestTag)
  : readPomBaselineVersion();

if (requiresPrereleaseId(bumpArg, baseVersion) && !prereleaseId) {
  console.error(
    `--prerelease-id is required for bump=${bumpArg} (use rc, beta, or alpha)`,
  );
  process.exit(1);
}
if (prereleaseId && !ALLOWED_PRE_IDS.has(prereleaseId)) {
  console.error(
    `Invalid --prerelease-id=${prereleaseId} (use rc, beta, or alpha)`,
  );
  process.exit(1);
}

const nextVersion = formatVersion(
  bumpVersion(baseVersion, bumpArg, prereleaseId),
);

writeOpenAdtParentVersion(join(root, "pom.xml"), nextVersion);
syncModuleParentVersions(nextVersion);
repairCliDependencyPluginVersion();

const wingetVersions = listWingetVersionDirs();
const wingetTemplate = wingetVersions.at(0) ?? "1.0.0";
if (wingetTemplate !== nextVersion) {
  syncWingetManifests(nextVersion, wingetTemplate);
}

updateHomebrew(nextVersion);
updateScoop(nextVersion);

console.log(`Release version: ${nextVersion}`);
console.log(`Tag: v${nextVersion}`);
writeGithubOutput(nextVersion);
