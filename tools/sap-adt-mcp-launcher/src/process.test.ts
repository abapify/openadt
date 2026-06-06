import { describe, expect, test } from "bun:test";
import { windowsTaskkillPath } from "./process.ts";

describe("windowsTaskkillPath", () => {
  test("does not rely on PATH", () => {
    const path = windowsTaskkillPath();
    if (process.platform === "win32") {
      expect(path).toMatch(/taskkill\.exe$/i);
      expect(path).toContain("System32");
    } else {
      // On non-Windows the resolver still returns the canonical C:\Windows
      // path (used as documentation) but does not assert on file existence.
      expect(path === undefined || /taskkill\.exe$/i.test(path ?? "")).toBe(
        true,
      );
    }
  });
});
