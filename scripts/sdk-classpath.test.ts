import { describe, expect, test } from "bun:test";
import { mkdirSync, mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import {
  RUNTIME_SAP_LIB_MIN_JARS,
  countSapJars,
  resolveSapBundleDirs,
} from "./sdk-classpath.ts";

function writeJarDir(dir: string, count: number): void {
  mkdirSync(dir, { recursive: true });
  for (let i = 0; i < count; i++) {
    writeFileSync(join(dir, `bundle-${i}.jar`), "");
  }
}

describe("resolveSapBundleDirs", () => {
  test("prefers runtime sap-lib when it has enough bundles", () => {
    const root = mkdtempSync(join(tmpdir(), "openadt-cp-"));
    const runtime = join(root, "runtime");
    const project = join(root, "project");
    const p2 = join(root, "p2");
    writeJarDir(runtime, RUNTIME_SAP_LIB_MIN_JARS);
    writeJarDir(project, 2);
    writeJarDir(p2, 5);
    const dirs = resolveSapBundleDirs({
      runtimeSapLibDir: runtime,
      projectSapLibDir: project,
      p2Dir: p2,
    });
    expect(dirs).toEqual([{ path: runtime, kind: "sap-lib" }]);
  });

  test("falls back to p2 when only a minimal Maven sap-lib exists", () => {
    const root = mkdtempSync(join(tmpdir(), "openadt-cp-"));
    const runtime = join(root, "runtime-missing");
    const project = join(root, "project");
    const p2 = join(root, "p2");
    writeJarDir(project, 2);
    writeJarDir(p2, 5);
    const dirs = resolveSapBundleDirs({
      runtimeSapLibDir: runtime,
      projectSapLibDir: project,
      p2Dir: p2,
    });
    expect(dirs).toEqual([{ path: p2, kind: "p2" }]);
  });
});

describe("countSapJars", () => {
  test("returns zero for missing directory", () => {
    expect(countSapJars(join(tmpdir(), "openadt-missing-" + Date.now()))).toBe(
      0,
    );
  });

  test("counts jar files only", () => {
    const root = mkdtempSync(join(tmpdir(), "openadt-cp-"));
    const dir = join(root, "small");
    writeJarDir(dir, 2);
    writeFileSync(join(dir, "readme.txt"), "");
    expect(countSapJars(dir)).toBe(2);
  });
});
