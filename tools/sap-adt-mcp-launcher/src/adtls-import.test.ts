import { describe, expect, test } from "bun:test";
import { mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";
import {
  adtlsDestinationsStorePath,
  destinationsFromAdtlsStore,
  discoverAdtlsDestinations,
  materializeAdtlsDestinations,
} from "./adtls-import.ts";

describe("adtls-import", () => {
  test("reads store and builds abap workspace URIs", () => {
    const home = join(tmpdir(), `adtls-${Date.now()}`);
    mkdirSync(home, { recursive: true });
    writeFileSync(
      join(home, "destinations.json"),
      JSON.stringify({
        formatVersion: "1.0",
        destinations: [
          {
            id: "DEV_100_developer_en",
            protocol: "rfc",
            properties: { systemId: "DEV", client: "100", user: "DEVELOPER" },
          },
        ],
      }),
      "utf8",
    );

    const prev = process.env.ADTLS_HOME;
    process.env.ADTLS_HOME = home;
    try {
      expect(adtlsDestinationsStorePath()).toBe(
        join(home, "destinations.json"),
      );
      const found = discoverAdtlsDestinations(join(home, "ws"));
      expect(found?.destinations.length).toBe(1);
      expect(found?.destinations[0]?.workspaceFolderUri).toBe(
        "abap:/DEV_100_developer_en",
      );
      expect(found?.storePath).toBe(join(home));
    } finally {
      if (prev === undefined) {
        delete process.env.ADTLS_HOME;
      } else {
        process.env.ADTLS_HOME = prev;
      }
      rmSync(home, { recursive: true, force: true });
    }
  });

  test("destinationsFromAdtlsStore skips blank ids", () => {
    const imported = destinationsFromAdtlsStore(
      { destinations: [{ id: "  " }, { id: "DEV_100_developer_en" }] },
      "/ws",
    );
    expect(imported.length).toBe(1);
  });

  test("materializeAdtlsDestinations writes semantic cache", () => {
    const ws = join(tmpdir(), `adtls-mat-${Date.now()}`);
    const store = {
      destinations: [
        {
          id: "DEV_100_developer_en",
          properties: {
            systemId: "DEV",
            client: "100",
            user: "DEVELOPER",
            language: "EN",
            ssoEnabled: "true",
            sncType: "9",
          },
        },
      ],
    };
    const out = materializeAdtlsDestinations(ws, store);
    expect(out.length).toBe(1);
    expect(out[0]?.propertiesPath).toContain(".destination.properties");
    const text = readFileSync(out[0]!.propertiesPath, "utf8");
    expect(text).toContain("SSOEnabled=1");
    rmSync(ws, { recursive: true, force: true });
  });
});
