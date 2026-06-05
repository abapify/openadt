import { describe, expect, test } from "bun:test";
import {
  buildAdtLscSpawnRuntime,
  ensureMinimalProcessEnv,
  isSecureLoginSecudir,
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

  test("includes configured native dirs in java.library.path", () => {
    const rt = buildAdtLscSpawnRuntime({
      jcoNativeDir: "C:\\SAP\\jco",
      sapcrypto: "C:\\SAP\\sapcrypto.dll",
    });
    const libPath = rt.jvmArgs.find((a) =>
      a.startsWith("-Djava.library.path="),
    );
    expect(libPath).toContain("jco");
    expect(libPath).toContain("SAP");
  });
});

describe("ensureMinimalProcessEnv", () => {
  test("fills Windows profile dirs when agent strips env", () => {
    if (process.platform !== "win32") {
      // The function is a no-op off-Windows; covered by typecheck.
      const env = ensureMinimalProcessEnv({ Path: "C:\\Users\\me\\.bun\\bin" });
      expect(env.Path).toBe("C:\\Users\\me\\.bun\\bin");
      return;
    }
    const env = ensureMinimalProcessEnv({
      Path: "C:\\Users\\me\\.bun\\bin",
    });
    expect(env.APPDATA).toContain("AppData");
    expect(env.LOCALAPPDATA).toContain("Local");
    expect(env.SystemRoot).toBe("C:\\Windows");
  });
});

describe("isSecureLoginSecudir", () => {
  test("rejects openadt HTTP CA sec folder", () => {
    expect(isSecureLoginSecudir("C:\\Users\\me\\.openadt\\sec")).toBe(false);
  });

  test("accepts SAP Common when present", () => {
    if (process.platform !== "win32") {
      // SAP Common path is Windows-only; the function still rejects our own
      // sec folder on every platform.
      expect(isSecureLoginSecudir("C:\\Users\\me\\.openadt\\sec")).toBe(false);
      return;
    }
    expect(
      isSecureLoginSecudir(
        "C:\\Program Files\\SAP\\FrontEnd\\SecureLogin\\lib",
      ),
    ).toBe(true);
  });
});
