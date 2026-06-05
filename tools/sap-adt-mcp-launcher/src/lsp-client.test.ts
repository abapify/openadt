import { describe, expect, test } from "bun:test";
import { buildLspInitializeParams } from "./lsp-client.ts";

describe("buildLspInitializeParams", () => {
  test("includes userAgentInfos like VS Code (adt-lsc logon requires it)", () => {
    const params = buildLspInitializeParams({
      extensionRoot: "/ext",
      adtLscPath: "/ext/adt-lsc",
      adtLsRoot: "/ext/adt-ls",
      version: "1.0.0",
    }) as {
      initializationOptions?: {
        userAgentInfos?: Array<{ name: string; version: string }>;
      };
    };
    const infos = params.initializationOptions?.userAgentInfos ?? [];
    expect(infos.length).toBeGreaterThan(0);
    expect(
      infos.some((i) => i.name === "ADTVSCode" && i.version === "1.0.0"),
    ).toBe(true);
  });
});
