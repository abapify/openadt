import { describe, expect, test } from "bun:test";
import { mkdtempSync, mkdirSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import {
  findExtensionRoots,
  pickNewestExtension,
  resolveAdtLscFromExtension,
} from "./locate.ts";
import { redactToken, cursorMcpSnippet } from "./mcp.ts";

describe("pickNewestExtension", () => {
  test("sorts semver-like folder versions", () => {
    const picked = pickNewestExtension([
      { path: "/a/sapse.adt-vscode-1.0.0-win32-x64", version: "1.0.0" },
      { path: "/b/sapse.adt-vscode-1.1.0-win32-x64", version: "1.1.0" },
    ]);
    expect(picked?.version).toBe("1.1.0");
  });

  test("returns undefined for empty list", () => {
    expect(pickNewestExtension([])).toBeUndefined();
  });
});

describe("resolveAdtLscFromExtension", () => {
  test("finds win32 adt-lsc.exe in fixture layout", () => {
    const root = mkdtempSync(join(tmpdir(), "adt-ext-"));
    const rel = join("adt-ls", "win32", "win32", "x86_64", "adt-lsc.exe");
    const adtLsc = join(root, rel);
    mkdirSync(join(root, "adt-ls", "win32", "win32", "x86_64"), {
      recursive: true,
    });
    writeFileSync(adtLsc, "", { flag: "w" });

    if (process.platform === "win32") {
      expect(resolveAdtLscFromExtension(root)).toBe(adtLsc);
    } else {
      expect(resolveAdtLscFromExtension(root)).toBeUndefined();
    }
  });
});

describe("findExtensionRoots", () => {
  test("returns array (may be empty in CI)", () => {
    expect(Array.isArray(findExtensionRoots())).toBe(true);
  });
});

describe("mcp helpers", () => {
  test("redactToken hides middle", () => {
    expect(redactToken("abcdefgh-ijkl")).toMatch(/^abcd/);
    expect(redactToken("abcdefgh-ijkl")).not.toContain("ijkl-mnop");
  });

  test("cursorMcpSnippet shape", () => {
    const snippet = cursorMcpSnippet(2236, "secret-token") as {
      mcpServers: Record<
        string,
        { url: string; headers: Record<string, string> }
      >;
    };
    expect(snippet.mcpServers["sap-adt"].url).toBe("http://localhost:2236/mcp");
    expect(snippet.mcpServers["sap-adt"].headers.Authorization).toBe(
      "Bearer secret-token",
    );
  });
});
