/**
 * SDK/SNC classpath: one canonical JCo core jar first, then ADT/Eclipse bundles (no duplicate JCo cores).
 * Mirrors scripts/openadt-sdk.ps1 — SAP JCo rejects com.sap.conn.jco_*.jar and jco-*.jar on the classpath.
 */
import {
  copyFileSync,
  existsSync,
  mkdirSync,
  readdirSync,
  statSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { basename, join } from "node:path";

const JCO_CORE_PATTERN =
  /^(?:com\.sap\.conn\.jco[_-]|jco-)(\d+(?:\.\d+)+)\.jar$/i;
/** Eclipse p2 bundle file names */
const P2_SAP_BUNDLE_PATTERN = /^(com\.sap\.(adt|conn)|org\.(eclipse|osgi)\.)/;
/** Maven copy-dependencies names under target/sap-lib (communication-3.58.0.jar, jco-3.1.13.jar, …) */
const MAVEN_SAP_LIB_PATTERN =
  /^(communication|compatibility|destinations|destinations-model|logging|util|jco|jco-eclipse)-|^(core-|equinox-|osgi|service\.prefs)|^core-net-/;

/** Bundles Maven sap-lib omits but headless ADT SDK still needs (from Eclipse p2). */
const P2_SUPPLEMENT_PREFIXES = ["org.eclipse.core.net_"];

/** Windows release copies a minimal Maven sap-lib; full Eclipse p2 is required for `adt` and EMF. */
export const RUNTIME_SAP_LIB_MIN_JARS = 100;

export function countSapJars(dir: string): number {
  if (!existsSync(dir)) {
    return 0;
  }
  return readdirSync(dir).filter((name) => name.endsWith(".jar")).length;
}

/**
 * Prefer a complete bundle dir (runtime copy or Eclipse p2). Partial target/sap-lib breaks `adt discover`.
 */
export function resolveSapBundleDirs(options: {
  runtimeSapLibDir: string;
  projectSapLibDir: string;
  p2Dir: string;
}): SapBundleDir[] {
  const { runtimeSapLibDir, projectSapLibDir, p2Dir } = options;
  if (countSapJars(runtimeSapLibDir) >= RUNTIME_SAP_LIB_MIN_JARS) {
    return [{ path: runtimeSapLibDir, kind: "sap-lib" }];
  }
  if (countSapJars(projectSapLibDir) >= RUNTIME_SAP_LIB_MIN_JARS) {
    return [{ path: projectSapLibDir, kind: "sap-lib" }];
  }
  if (existsSync(p2Dir)) {
    return [{ path: p2Dir, kind: "p2" }];
  }
  if (countSapJars(projectSapLibDir) > 0) {
    return [{ path: projectSapLibDir, kind: "sap-lib" }];
  }
  return [];
}

export function isJcoCoreJar(fileName: string): boolean {
  return JCO_CORE_PATTERN.test(fileName);
}

export function isSapBundleJar(
  fileName: string,
  dirKind: "sap-lib" | "p2",
): boolean {
  if (dirKind === "sap-lib") {
    return MAVEN_SAP_LIB_PATTERN.test(fileName);
  }
  return P2_SAP_BUNDLE_PATTERN.test(fileName);
}

export function hasMinimalSdkBundles(entries: string[]): boolean {
  const names = entries.map((entry) => basename(entry).toLowerCase());
  const hasDestinationsModel = names.some(
    (name) =>
      name.includes("destinations.model") ||
      name.startsWith("destinations-model"),
  );
  const hasJco = names.some(
    (name) => isJcoCoreJar(name) || name.startsWith("com.sap.conn.jco-"),
  );
  const hasCommunication = names.some(
    (name) =>
      name.includes("adt.communication") || name.startsWith("communication-"),
  );
  const hasCoreNet = names.some(
    (name) =>
      name.startsWith("org.eclipse.core.net_") || name.startsWith("core-net-"),
  );
  return hasDestinationsModel && hasJco && hasCommunication && hasCoreNet;
}

export function supplementFromP2(entries: string[], p2Dir: string): string[] {
  if (!existsSync(p2Dir)) {
    return entries;
  }
  const result = [...entries];
  const names = new Set(result.map((entry) => basename(entry).toLowerCase()));
  for (const prefix of P2_SUPPLEMENT_PREFIXES) {
    const prefixLower = prefix.toLowerCase();
    if ([...names].some((name) => name.startsWith(prefixLower))) {
      continue;
    }
    const match = readdirSync(p2Dir)
      .filter((name) => name.startsWith(prefix) && name.endsWith(".jar"))
      .sort((a, b) => b.localeCompare(a))[0];
    if (match) {
      const path = join(p2Dir, match);
      result.push(path);
      names.add(match.toLowerCase());
    }
  }
  return result;
}

export function canonicalJcoJarPath(sourcePath: string): string {
  const name = basename(sourcePath);
  const match = JCO_CORE_PATTERN.exec(name);
  if (!match) {
    return sourcePath;
  }
  const canonical = `com.sap.conn.jco-${match[1]}.jar`;
  if (name.toLowerCase() === canonical.toLowerCase()) {
    return sourcePath;
  }
  const cacheDir = join(tmpdir(), "openadt-jco-lib");
  mkdirSync(cacheDir, { recursive: true });
  const dest = join(cacheDir, canonical);
  const srcStat = statSync(sourcePath);
  if (srcStat.size <= 0) {
    throw new Error(`JCo jar is empty: ${sourcePath}`);
  }
  const destStale =
    !existsSync(dest) ||
    statSync(dest).size <= 0 ||
    statSync(dest).mtimeMs < srcStat.mtimeMs ||
    statSync(dest).size !== srcStat.size;
  if (destStale) {
    copyFileSync(sourcePath, dest);
  }
  const destStat = statSync(dest);
  if (destStat.size !== srcStat.size) {
    throw new Error(
      `Canonical JCo copy failed: ${dest} (${destStat.size} bytes, expected ${srcStat.size})`,
    );
  }
  return dest;
}

function listSapBundles(dir: string, dirKind: "sap-lib" | "p2"): string[] {
  if (!existsSync(dir)) {
    return [];
  }
  return readdirSync(dir)
    .filter((name) => name.endsWith(".jar") && isSapBundleJar(name, dirKind))
    .map((name) => join(dir, name));
}

export type SapBundleDir = { path: string; kind: "sap-lib" | "p2" };

export function buildSdkClasspathEntries(options: {
  classesDir?: string;
  appJar: string;
  sapDirs: SapBundleDir[];
}): string[] {
  const entries: string[] = [];
  if (options.classesDir && existsSync(options.classesDir)) {
    entries.push(options.classesDir);
  }
  entries.push(options.appJar);

  const sapJars = options.sapDirs.flatMap(({ path, kind }) =>
    listSapBundles(path, kind),
  );
  const jcoCore = sapJars.filter((path) => isJcoCoreJar(basename(path)));
  const nonJco = sapJars.filter((path) => !isJcoCoreJar(basename(path)));

  if (jcoCore.length > 0) {
    const p2Style = jcoCore.filter((path) =>
      /^com\.sap\.conn\.jco[_-]/i.test(basename(path)),
    );
    const pool = p2Style.length > 0 ? p2Style : jcoCore;
    const source = [...pool].sort((a, b) =>
      basename(b).localeCompare(basename(a)),
    )[0]!;
    entries.push(canonicalJcoJarPath(source));
  }

  entries.push(...nonJco);
  return entries;
}
