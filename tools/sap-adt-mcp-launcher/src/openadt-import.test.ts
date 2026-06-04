import { describe, expect, test } from "bun:test";
import { mkdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import {
  buildAdtDestinationId,
  destinationPropertiesContent,
  loadOpenAdtDestinationEntries,
  materializeOpenAdtDestinations,
} from "./openadt-import.ts";

describe("buildAdtDestinationId", () => {
  test("formats SYSTEM_CLIENT_USER_LANG", () => {
    expect(
      buildAdtDestinationId({
        alias: "DEV",
        systemId: "DEV",
        client: "100",
        user: "DEVELOPER",
        language: "EN",
        jco: { mshost: "dev-ms.example.com" },
      }),
    ).toBe("DEV_100_DEVELOPER_EN");
  });
});

describe("destinationPropertiesContent", () => {
  test("maps jco fields to eclipse keys", () => {
    const body = destinationPropertiesContent({
      alias: "DEV",
      systemId: "DEV",
      client: "100",
      user: "DEVELOPER",
      language: "EN",
      jco: {
        mshost: "dev-ms.example.com",
        msserv: "3600",
        group: "PUBLIC",
        snc_partnername: "p:CN=SAPServiceDEV",
        snc_sso: "1",
        snc_qop: "9",
      },
    });
    expect(body).toContain("id=DEV_100_DEVELOPER_EN");
    expect(body).toContain("messageServer=dev-ms.example.com");
    expect(body).toContain("SSOEnabled=1");
    expect(body).toContain("SNCType=9");
  });
});

describe("materializeOpenAdtDestinations", () => {
  test("writes semantic cache from fixture toml", () => {
    const home = join(tmpdir(), `openadt-import-${Date.now()}`);
    const destDir = join(home, "destinations");
    mkdirSync(destDir, { recursive: true });
    writeFileSync(
      join(destDir, "fixture.openadt.toml"),
      `
version = 1
[destinations.DEV]
alias = "DEV"
system_id = "DEV"
client = "100"
user = "DEVELOPER"
language = "EN"
[destinations.DEV.jco]
mshost = "dev-ms.example.com"
msserv = "3600"
group = "PUBLIC"
`,
      "utf8",
    );

    const prevHome = process.env.OPENADT_HOME;
    process.env.OPENADT_HOME = home;
    try {
      const ws = join(home, "adt-ls-workspace");
      const imported = materializeOpenAdtDestinations(ws);
      expect(imported.length).toBe(1);
      expect(imported[0]?.id).toBe("DEV_100_DEVELOPER_EN");
      expect(imported[0]?.workspaceFolderUri).toBe(
        "abap:/DEV_100_DEVELOPER_EN",
      );
    } finally {
      if (prevHome === undefined) {
        delete process.env.OPENADT_HOME;
      } else {
        process.env.OPENADT_HOME = prevHome;
      }
      rmSync(home, { recursive: true, force: true });
    }
  });
});

describe("loadOpenAdtDestinationEntries", () => {
  test("returns array on user machine or empty in CI", () => {
    const entries = loadOpenAdtDestinationEntries();
    expect(Array.isArray(entries)).toBe(true);
  });
});
