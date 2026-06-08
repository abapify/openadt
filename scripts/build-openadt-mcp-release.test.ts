import { describe, expect, test } from "bun:test";
import { parsePlatform } from "./build-openadt-mcp-release.ts";

describe("parsePlatform", () => {
  test("accepts the 4 matrix values", () => {
    for (const p of ["win-x64", "linux-x64", "darwin-arm64", "darwin-x64"]) {
      expect(parsePlatform(p)).toBe(p);
    }
  });

  test("rejects unknown values", () => {
    expect(() => parsePlatform("win32")).toThrow();
    expect(() => parsePlatform("linux-arm64")).toThrow();
    expect(() => parsePlatform("")).toThrow();
    expect(() => parsePlatform(undefined)).toThrow();
  });
});
