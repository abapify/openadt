import { describe, expect, test } from "bun:test";
import {
  buildAdtLscSpawnRuntime,
  getEnv,
  getEnvInt,
  getEnvPath,
  isVsCodeAdtWorkspacePath,
} from "./runtime-env.ts";

describe("isVsCodeAdtWorkspacePath", () => {
  test("detects VS Code workspaceStorage adtWorkspace", () => {
    expect(
      isVsCodeAdtWorkspacePath(
        "C:\\Users\\me\\AppData\\Roaming\\Code\\User\\workspaceStorage\\abc\\SAPSE.adt-vscode\\adtWorkspace",
      ),
    ).toBe(true);
  });

  test("rejects openadt default workspace", () => {
    expect(
      isVsCodeAdtWorkspacePath("C:\\Users\\me\\.openadt\\adt-ls-workspace"),
    ).toBe(false);
  });
});

describe("buildAdtLscSpawnRuntime", () => {
  test("adds sapcrypto JVM arg when configured", () => {
    const rt = buildAdtLscSpawnRuntime({
      sapcrypto: "C:\\SAP\\sapcrypto.dll",
      jcoNativeDir: "C:\\SAP\\jco",
    });
    expect(rt.jvmArgs.some((a) => a.includes("snc_lib"))).toBe(true);
    expect(rt.jvmArgs.some((a) => a.includes("java.library.path"))).toBe(true);
  });
});

describe("getEnv", () => {
  test("returns trimmed value", () => {
    process.env.OPENADT_TEST_GETENV = "  hello  ";
    try {
      expect(getEnv("OPENADT_TEST_GETENV")).toBe("hello");
    } finally {
      delete process.env.OPENADT_TEST_GETENV;
    }
  });

  test("returns default when unset", () => {
    delete process.env.OPENADT_TEST_GETENV_MISSING;
    expect(getEnv("OPENADT_TEST_GETENV_MISSING", { default: "fallback" })).toBe(
      "fallback",
    );
  });

  test("throws when required and missing", () => {
    delete process.env.OPENADT_TEST_GETENV_MISSING;
    expect(() =>
      getEnv("OPENADT_TEST_GETENV_MISSING", { required: true }),
    ).toThrow(/Missing required/);
  });
});

describe("getEnvInt", () => {
  test("parses integer", () => {
    process.env.OPENADT_TEST_PORT = "2236";
    try {
      expect(getEnvInt("OPENADT_TEST_PORT", { min: 1, max: 65535 })).toBe(2236);
    } finally {
      delete process.env.OPENADT_TEST_PORT;
    }
  });

  test("rejects out-of-range", () => {
    process.env.OPENADT_TEST_PORT = "99999";
    try {
      expect(() =>
        getEnvInt("OPENADT_TEST_PORT", { min: 1, max: 65535 }),
      ).toThrow(/above max/);
    } finally {
      delete process.env.OPENADT_TEST_PORT;
    }
  });
});

describe("getEnvPath", () => {
  test("returns undefined when mustExist and missing", () => {
    process.env.OPENADT_TEST_PATH = "/nonexistent/openadt-test";
    expect(
      getEnvPath("OPENADT_TEST_PATH", { mustExist: true }),
    ).toBeUndefined();
    delete process.env.OPENADT_TEST_PATH;
  });
});
