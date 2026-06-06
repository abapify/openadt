import { describe, expect, test } from "bun:test";
import {
  buildAdtLscSpawnRuntime,
  Env,
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

describe("Env", () => {
  test("string returns trimmed value", () => {
    process.env.OPENADT_TEST_GETENV = "  hello  ";
    try {
      expect(Env.fromProcess().string("OPENADT_TEST_GETENV")).toBe("hello");
    } finally {
      delete process.env.OPENADT_TEST_GETENV;
    }
  });

  test("string returns default when unset", () => {
    delete process.env.OPENADT_TEST_GETENV_MISSING;
    expect(
      Env.fromProcess().string("OPENADT_TEST_GETENV_MISSING", {
        default: "fallback",
      }),
    ).toBe("fallback");
  });

  test("string throws when required and missing", () => {
    delete process.env.OPENADT_TEST_GETENV_MISSING;
    expect(() =>
      Env.fromProcess().string("OPENADT_TEST_GETENV_MISSING", {
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
        Env.fromProcess().integer("OPENADT_TEST_PORT", { min: 1, max: 65535 });
      if (throws) {
        expect(result).toThrow(throws);
      } else {
        expect(result()).toBe(expected);
      }
    } finally {
      delete process.env.OPENADT_TEST_PORT;
    }
  });

  test("integer returns undefined when unset", () => {
    delete process.env.OPENADT_TEST_PORT_MISSING;
    expect(
      Env.fromProcess().integer("OPENADT_TEST_PORT_MISSING", {
        min: 1,
        max: 65535,
      }),
    ).toBeUndefined();
  });

  test("path returns undefined when mustExist and missing", () => {
    process.env.OPENADT_TEST_PATH = "/nonexistent/openadt-test";
    expect(
      Env.fromProcess().path("OPENADT_TEST_PATH", { mustExist: true }),
    ).toBeUndefined();
    delete process.env.OPENADT_TEST_PATH;
  });
});
