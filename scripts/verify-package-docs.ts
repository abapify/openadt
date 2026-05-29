#!/usr/bin/env bun
/** CI guard: leaf packages need package-info.java or an ARCHITECTURE.md Package-to-spec row. */
import { readFileSync, readdirSync, statSync, existsSync } from "node:fs";
import { join } from "node:path";

const root = join(import.meta.dir, "..");
const appsDir = join(root, "apps");
const architecturePath = join(appsDir, "ARCHITECTURE.md");

function listJavaPackages(): Set<string> {
  const packages = new Set<string>();
  for (const module of readdirSync(appsDir)) {
    const javaRoot = join(
      appsDir,
      module,
      "src",
      "main",
      "java",
      "org",
      "openadt",
    );
    if (!existsSync(javaRoot)) continue;
    walk(javaRoot, "org.openadt", packages);
  }
  return packages;
}

function walk(dir: string, pkg: string, packages: Set<string>): void {
  const entries = readdirSync(dir);
  let hasJava = false;
  for (const name of entries) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) {
      walk(full, `${pkg}.${name}`, packages);
    } else if (name.endsWith(".java") && name !== "package-info.java") {
      hasJava = true;
    }
  }
  if (hasJava) {
    packages.add(pkg);
  }
}

function hasPackageInfo(pkgPath: string): boolean {
  const slashPkg = pkgPath.replace(/\./g, "/");
  for (const module of readdirSync(appsDir)) {
    const info = join(
      appsDir,
      module,
      "src",
      "main",
      "java",
      slashPkg,
      "package-info.java",
    );
    if (existsSync(info)) return true;
  }
  return false;
}

function packagesInArchitecture(md: string): Set<string> {
  const documented = new Set<string>();
  const row = /`org\.openadt\.[^`]+`/g;
  for (const match of md.matchAll(row)) {
    documented.add(match[0].slice(1, -1));
  }
  return documented;
}

const packages = listJavaPackages();
const architecture = readFileSync(architecturePath, "utf8");
const documented = packagesInArchitecture(architecture);
const errors: string[] = [];

for (const pkg of [...packages].sort()) {
  if (hasPackageInfo(pkg)) continue;
  if (documented.has(pkg)) continue;
  errors.push(`missing package-info.java and ARCHITECTURE entry: ${pkg}`);
}

if (errors.length > 0) {
  console.error(
    "verify-package-docs failed:\n" + errors.map((e) => `  - ${e}`).join("\n"),
  );
  process.exit(1);
}

console.log(`verify-package-docs: OK (${packages.size} packages)`);
