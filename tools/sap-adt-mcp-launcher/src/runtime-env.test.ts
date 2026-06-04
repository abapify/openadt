import { describe, expect, test } from "bun:test";
import {
  buildAdtLscSpawnRuntime,
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
