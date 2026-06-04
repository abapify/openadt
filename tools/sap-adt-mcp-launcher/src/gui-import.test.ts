import { describe, expect, test } from "bun:test";
import { mkdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import {
  buildAbapWorkspaceFolderUri,
  discoverGuiDestinations,
  readDestinationId,
} from "./gui-import.ts";

describe("buildAbapWorkspaceFolderUri", () => {
  test("uses abap scheme and destination id path", () => {
    expect(buildAbapWorkspaceFolderUri("DEV_100_developer_en")).toBe(
      "abap:/DEV_100_developer_en",
    );
  });
});

describe("readDestinationId", () => {
  test("reads id property", () => {
    const dir = join(tmpdir(), `gui-import-${Date.now()}`);
    mkdirSync(dir, { recursive: true });
    const file = join(dir, ".destination.properties");
    writeFileSync(
      file,
      "# comment\nid=DEV_100_developer_en\nclient=100\n",
      "utf8",
    );
    expect(readDestinationId(file)).toBe("DEV_100_developer_en");
  });
});

function withIsolatedHome(run: () => void): void {
  const prevAppData = process.env.APPDATA;
  const prevHome = process.env.USERPROFILE;
  const prevHomeDrive = process.env.HOMEDRIVE;
  const prevHomePath = process.env.HOMEPATH;
  const isolated = join(tmpdir(), `openadt-home-${Date.now()}`);
  mkdirSync(isolated, { recursive: true });
  process.env.APPDATA = join(isolated, "Roaming");
  process.env.USERPROFILE = isolated;
  delete process.env.HOMEDRIVE;
  delete process.env.HOMEPATH;
  try {
    run();
  } finally {
    if (prevAppData === undefined) {
      delete process.env.APPDATA;
    } else {
      process.env.APPDATA = prevAppData;
    }
    if (prevHome === undefined) {
      delete process.env.USERPROFILE;
    } else {
      process.env.USERPROFILE = prevHome;
    }
    if (prevHomeDrive === undefined) {
      delete process.env.HOMEDRIVE;
    } else {
      process.env.HOMEDRIVE = prevHomeDrive;
    }
    if (prevHomePath === undefined) {
      delete process.env.HOMEPATH;
    } else {
      process.env.HOMEPATH = prevHomePath;
    }
    rmSync(isolated, { recursive: true, force: true });
  }
}

describe("discoverGuiDestinations", () => {
  test("returns undefined when no GUI storage (CI)", () => {
    withIsolatedHome(() => {
      expect(discoverGuiDestinations()).toBeUndefined();
    });
  });

  test("finds destinations under synthetic VS Code workspaceStorage", () => {
    withIsolatedHome(() => {
      const fakeRoaming = process.env.APPDATA!;
      const destDir = join(
        fakeRoaming,
        "Code",
        "User",
        "workspaceStorage",
        "abc123",
        "SAPSE.adt-vscode",
        "adtWorkspace",
        ".metadata",
        ".plugins",
        "org.eclipse.core.resources.semantic",
        ".cache",
        "DEV_100_developer_en",
      );
      mkdirSync(destDir, { recursive: true });
      writeFileSync(
        join(destDir, ".destination.properties"),
        "id=DEV_100_developer_en\nclient=100\n",
        "utf8",
      );
      const bundle = discoverGuiDestinations();
      expect(bundle?.destinations.length).toBe(1);
      expect(bundle?.destinations[0]?.id).toBe("DEV_100_developer_en");
      expect(bundle?.destinations[0]?.workspaceFolderUri).toBe(
        "abap:/DEV_100_developer_en",
      );
    });
  });
});
