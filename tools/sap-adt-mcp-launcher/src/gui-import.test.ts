import { describe, expect, test } from "bun:test";
import { mkdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import {
  buildAbapWorkspaceFolderUri,
  destinationFileUris,
  discoverGuiDestinations,
  readDestinationId,
  resolveDestinationImport,
} from "./gui-import.ts";

describe("buildAbapWorkspaceFolderUri", () => {
  test("uses abap scheme and destination id path", () => {
    expect(buildAbapWorkspaceFolderUri("DEV_100_developer_en")).toBe(
      "abap:/DEV_100_developer_en",
    );
  });
});

describe("destinationFileUris", () => {
  test.each<{
    label: string;
    input: string;
    matches: RegExp | string;
  }>([
    {
      label: "resolves relative propertiesPath before pathToFileURL",
      input: "rel/.destination.properties",
      matches: /^file:\/\/\/.+rel\/\.destination\.properties$/,
    },
    {
      label: "passes absolute propertiesPath through unchanged",
      input: "/abs/path/.destination.properties",
      matches: "file:///abs/path/.destination.properties",
    },
  ])("$label", ({ input, matches }) => {
    const uris = destinationFileUris([
      {
        id: "X",
        workspaceFolderUri: "abap:/X",
        adtWorkspacePath: "/abs/path",
        propertiesPath: input,
      },
    ]);
    expect(uris[0]).toMatch(matches);
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
  const prev: Record<string, string | undefined> = {
    APPDATA: process.env.APPDATA,
    USERPROFILE: process.env.USERPROFILE,
    HOMEDRIVE: process.env.HOMEDRIVE,
    HOMEPATH: process.env.HOMEPATH,
    HOME: process.env.HOME,
    XDG_CONFIG_HOME: process.env.XDG_CONFIG_HOME,
  };
  const isolated = join(
    tmpdir(),
    `openadt-home-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
  );
  mkdirSync(isolated, { recursive: true });
  process.env.APPDATA = join(isolated, "Roaming");
  process.env.USERPROFILE = isolated;
  delete process.env.HOMEDRIVE;
  delete process.env.HOMEPATH;
  process.env.HOME = isolated;
  process.env.XDG_CONFIG_HOME = join(isolated, ".config");
  try {
    run();
  } finally {
    for (const [key, value] of Object.entries(prev)) {
      if (value === undefined) {
        delete process.env[key];
      } else {
        process.env[key] = value;
      }
    }
    rmSync(isolated, { recursive: true, force: true });
  }
}

describe("destinationFileUris", () => {
  test("builds file URLs from properties paths", () => {
    const dir = join(tmpdir(), `gui-file-uri-${Date.now()}`);
    const props = join(dir, ".destination.properties");
    const uris = destinationFileUris([
      {
        id: "DEV_100_developer_en",
        workspaceFolderUri: "abap:/DEV_100_developer_en",
        adtWorkspacePath: dir,
        propertiesPath: props,
      },
    ]);
    expect(uris[0]).toContain(".destination.properties");
    expect(uris[0]?.startsWith("file:")).toBe(true);
  });
});

describe("resolveDestinationImport adtls", () => {
  test("returns materialized fileUris not destinations.json path", () => {
    const home = join(tmpdir(), `adtls-resolve-${Date.now()}`);
    mkdirSync(home, { recursive: true });
    writeFileSync(
      join(home, "destinations.json"),
      JSON.stringify({
        destinations: [
          {
            id: "DEV_100_developer_en",
            properties: { systemId: "DEV", client: "100", user: "DEVELOPER" },
          },
        ],
      }),
      "utf8",
    );
    const ws = join(home, "adt-ls-workspace");
    const prev = process.env.ADTLS_HOME;
    process.env.ADTLS_HOME = home;
    try {
      const resolved = resolveDestinationImport(ws, "adtls", false);
      expect(resolved.imported.length).toBe(1);
      expect(resolved.fileUris.length).toBe(1);
      expect(resolved.fileUris[0]).toContain(".destination.properties");
      expect(resolved.fileUris[0]).not.toContain("destinations.json");
      expect(resolved.destinationsStorePath).toBe(home);
    } finally {
      if (prev === undefined) {
        delete process.env.ADTLS_HOME;
      } else {
        process.env.ADTLS_HOME = prev;
      }
      rmSync(home, { recursive: true, force: true });
    }
  });
});

describe("discoverGuiDestinations", () => {
  test("returns undefined when no GUI storage (CI)", () => {
    withIsolatedHome(() => {
      expect(discoverGuiDestinations()).toBeUndefined();
    });
  });

  test("finds destinations under synthetic VS Code workspaceStorage", () => {
    withIsolatedHome(() => {
      const storageRoot =
        process.platform === "win32"
          ? join(process.env.APPDATA!, "Code", "User", "workspaceStorage")
          : join(
              process.env.XDG_CONFIG_HOME ?? join(process.env.HOME!, ".config"),
              "Code",
              "User",
              "workspaceStorage",
            );
      const destDir = join(
        storageRoot,
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
