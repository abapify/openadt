import { describe, expect, test } from "bun:test";
import { windowsTaskkillPath } from "./process.ts";

describe("windowsTaskkillPath", () => {
  test("does not rely on PATH", () => {
    const path = windowsTaskkillPath();
    expect(path).toMatch(/taskkill\.exe$/i);
    expect(path).toContain("System32");
  });
});
