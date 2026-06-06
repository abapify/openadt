import { describe, expect, test } from "bun:test";
import { writeFileSync, mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import {
  buildAdtLscSpawnRuntime,
  envVar,
  Env,
  isVsCodeAdtWorkspacePath,
  loadOpenAdtRuntimePaths,
  type WorkspacePath,
} from "./runtime-env.ts";

describe("isVsCodeAdtWorkspacePath", () => {
  test("detects VS Code workspaceStorage adtWorkspace", () => {
    expect(
      isVsCodeAdtWorkspacePath(
        "C:\\Users\\me\\AppData\\Roaming\\Code\\User\\workspaceStorage\\abc\\SAPSE.adt-vscode\\adtWorkspace" as WorkspacePath,
      ),
    ).toBe(true);
  });

  test("rejects openadt default workspace", () => {
    expect(
      isVsCodeAdtWorkspacePath(
        "C:\\Users\\me\\.openadt\\adt-ls-workspace" as WorkspacePath,
      ),
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

describe("Env", () => {
  const processEnv = () => Env.fromProcess();

  test("string returns trimmed value", () => {
    process.env.OPENADT_TEST_GETENV = "  hello  ";
    try {
      expect(processEnv().string({ name: envVar("OPENADT_TEST_GETENV") })).toBe(
        "hello",
      );
    } finally {
      delete process.env.OPENADT_TEST_GETENV;
    }
  });

  test.each<{
    label: string;
    envKey: string;
    accessor: (env: Env) => unknown;
    expected: unknown;
  }>([
    {
      label: "string returns default when unset",
      envKey: "OPENADT_TEST_GETENV_MISSING",
      accessor: (env) =>
        env.string({
          name: envVar("OPENADT_TEST_GETENV_MISSING"),
          default: "fallback",
        }),
      expected: "fallback",
    },
    {
      label: "integer returns undefined when unset",
      envKey: "OPENADT_TEST_PORT_MISSING",
      accessor: (env) =>
        env.integer({
          name: envVar("OPENADT_TEST_PORT_MISSING"),
          min: 1,
          max: 65535,
        }),
      expected: undefined,
    },
  ])("$label", ({ envKey, accessor, expected }) => {
    delete process.env[envKey];
    expect(accessor(processEnv())).toEqual(expected);
  });

  test("string throws when required and missing", () => {
    delete process.env.OPENADT_TEST_GETENV_MISSING;
    expect(() =>
      processEnv().string({
        name: envVar("OPENADT_TEST_GETENV_MISSING"),
        required: true,
      }),
    ).toThrow(/Missing required/);
  });

  test.each<{ input: string; expected?: number; throws?: RegExp }>([
    { input: "42", expected: 42 },
    { input: "2236", expected: 2236 },
    { input: "1.5", throws: /is not an integer/ },
    { input: "-1", throws: /below min/ },
    { input: "65536", throws: /above max/ },
    { input: "abc", throws: /is not an integer/ },
    { input: "99999", throws: /above max/ },
  ])("integer parses %s", ({ input, expected, throws }) => {
    process.env.OPENADT_TEST_PORT = input;
    try {
      const result = () =>
        processEnv().integer({
          name: envVar("OPENADT_TEST_PORT"),
          min: 1,
          max: 65535,
        });
      if (throws) {
        expect(result).toThrow(throws);
      } else {
        expect(result()).toBe(expected);
      }
    } finally {
      delete process.env.OPENADT_TEST_PORT;
    }
  });

  test("path returns undefined when mustExist and missing", () => {
    process.env.OPENADT_TEST_PATH = "/nonexistent/openadt-test";
    expect(
      processEnv().path({
        name: envVar("OPENADT_TEST_PATH"),
        mustExist: true,
      }),
    ).toBeUndefined();
    delete process.env.OPENADT_TEST_PATH;
  });

  test("set updates keyByUpper so subsequent lookups hit the new value", () => {
    const env = new Env({});
    env.set({
      name: envVar("LOCALAPPDATA"),
      value: "C:\\Users\\me\\AppData\\Local",
    });
    expect(env.getTrimmed(envVar("LOCALAPPDATA"))).toBe(
      "C:\\Users\\me\\AppData\\Local",
    );
    expect(env.getTrimmed(envVar("localappdata"))).toBe(
      "C:\\Users\\me\\AppData\\Local",
    );
    expect(env.getTrimmed(envVar("LOCALAPPDATA"))).toBeDefined();
  });
});

describe("loadOpenAdtRuntimePaths", () => {
  function withTomlFile(content: string, fn: (path: string) => void): void {
    const tmp = mkdtempSync(join(tmpdir(), "openadt-runtime-env-"));
    const path = join(tmp, "local.openadt.toml");
    try {
      writeFileSync(path, content);
      fn(path);
    } finally {
      rmSync(tmp, { recursive: true, force: true });
    }
  }

  test("merges legacy top-level + nested runtime per field", () => {
    withTomlFile(
      [
        "version = 1",
        'jco_native_dir = "C:\\\\SAP\\\\jco"',
        "",
        "[runtime]",
        'sapcrypto = "C:\\\\SAP\\\\sapcrypto.dll"',
      ].join("\n"),
      (path) => {
        expect(loadOpenAdtRuntimePaths({ configPath: path })).toEqual({
          jcoNativeDir: "C:\\SAP\\jco",
          sapcrypto: "C:\\SAP\\sapcrypto.dll",
        });
      },
    );
  });

  test("prefers nested runtime value over legacy top-level", () => {
    withTomlFile(
      [
        'jco_native_dir = "C:\\\\old\\\\jco"',
        "",
        "[runtime]",
        'jco_native_dir = "C:\\\\new\\\\jco"',
      ].join("\n"),
      (path) => {
        expect(loadOpenAdtRuntimePaths({ configPath: path }).jcoNativeDir).toBe(
          "C:\\new\\jco",
        );
      },
    );
  });
});
