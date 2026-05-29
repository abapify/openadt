#!/usr/bin/env bun
/**
 * CI guard: wired SetupAnalyzer detectors must match specs/setup.md and specs/cli.md lists.
 */
import { readFileSync } from "node:fs";
import { join } from "node:path";

const root = join(import.meta.dir, "..");

function readSetupAnalyzer(): string {
  const paths = [
    "apps/openadt-bootstrap/src/main/java/org/openadt/bootstrap/SetupAnalyzer.java",
    "apps/openadt-cli/src/main/java/org/openadt/bootstrap/SetupAnalyzer.java",
    "apps/openadt-cli/src/main/java/org/openadt/setup/SetupAnalyzer.java",
  ];
  for (const rel of paths) {
    try {
      return readFileSync(join(root, rel), "utf8");
    } catch {
      /* try next */
    }
  }
  throw new Error("SetupAnalyzer.java not found");
}

const REQUIRED_DETECTORS = [
  "SapGuiLandscapeDetector",
  "NwbcSystemDetector",
  "SapBusinessClientDetector",
  "EclipseAdtDetector",
  "SapRulesDetector",
] as const;

const setupMd = readFileSync(join(root, "specs/setup.md"), "utf8");
const cliMd = readFileSync(join(root, "specs/cli.md"), "utf8");
const analyzer = readSetupAnalyzer();

const errors: string[] = [];

for (const detector of REQUIRED_DETECTORS) {
  if (!analyzer.includes(`new ${detector}(`)) {
    errors.push(`SetupAnalyzer does not wire ${detector}()`);
  }
  if (!setupMd.includes(detector) && !cliMd.includes(detector)) {
    errors.push(`specs do not mention ${detector}`);
  }
}

if (!analyzer.includes("RuntimeDetector")) {
  errors.push("SetupAnalyzer must use RuntimeDetector");
}
if (!analyzer.includes("SecureLoginDetector")) {
  errors.push("SetupAnalyzer must use SecureLoginDetector");
}

if (errors.length > 0) {
  console.error(
    "verify-spec-sync failed:\n" + errors.map((e) => `  - ${e}`).join("\n"),
  );
  process.exit(1);
}

console.log("verify-spec-sync: OK");
