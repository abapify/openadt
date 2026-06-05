import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";
import {
  adtlsImportSourceLabel,
  discoverAdtlsDestinations,
  loadAdtlsDestinationsStore,
  materializeAdtlsDestinations,
} from "./adtls-import.ts";
import {
  materializeOpenAdtDestinations,
  openAdtImportSourceLabel,
} from "./openadt-import.ts";
import { DESTINATION_FILE, SEMANTIC_CACHE_SUFFIX } from "./cache-paths.ts";
import { DEFAULT_WORKSPACE } from "./config.ts";
import { isVsCodeAdtWorkspacePath } from "./runtime-env.ts";
import type { DestinationImportMode } from "./types.ts";

/** VS Code / Cursor virtual workspace folder scheme for SAP ADT destinations. */
export const ABAP_WORKSPACE_SCHEME = "abap";

export { SEMANTIC_CACHE_SUFFIX, DESTINATION_FILE };

const ADT_WORKSPACE_SUFFIX = join("SAPSE.adt-vscode", "adtWorkspace");

export type GuiDestination = {
  id: string;
  workspaceFolderUri: string;
  adtWorkspacePath: string;
  propertiesPath: string;
};

export type GuiImportBundle = {
  adtWorkspacePath: string;
  destinations: GuiDestination[];
  /** workspaceStorage roots that contributed destinations */
  sources: string[];
};

/** Build the `abap:/<destinationId>` folder URI the ADT VS Code extension uses. */
export function buildAbapWorkspaceFolderUri(destinationId: string): string {
  const trimmed = destinationId.trim();
  if (!trimmed) {
    throw new Error("destination id is empty");
  }
  return `${ABAP_WORKSPACE_SCHEME}:/${trimmed}`;
}

/** Parse `id=` from Eclipse `.destination.properties` (no multiline values). */
export function readDestinationId(propertiesPath: string): string | undefined {
  const text = readFileSync(propertiesPath, "utf8");
  for (const line of text.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) {
      continue;
    }
    const eq = trimmed.indexOf("=");
    if (eq < 0) {
      continue;
    }
    const key = trimmed.slice(0, eq).trim();
    if (key === "id") {
      const value = trimmed.slice(eq + 1).trim();
      return value || undefined;
    }
  }
  return undefined;
}

function eclipseWorkspaceCandidates(): string[] {
  const home = homedir();
  const candidates = [
    join(home, "workspace"),
    join(home, "eclipse-workspace"),
    join(home, "Documents", "workspace"),
    join(home, "Documents", "eclipse-workspace"),
  ];
  return candidates.filter((p) => existsSync(p));
}

function listWorkspaceStorageRoots(): string[] {
  const home = homedir();
  const xdgConfig =
    process.env.XDG_CONFIG_HOME?.trim() || join(home, ".config");
  const roots: string[] = [];
  if (process.platform === "win32") {
    const appData = process.env.APPDATA;
    if (appData) {
      roots.push(join(appData, "Code", "User", "workspaceStorage"));
      roots.push(join(appData, "Cursor", "User", "workspaceStorage"));
    }
  } else if (process.platform === "darwin") {
    roots.push(
      join(
        home,
        "Library",
        "Application Support",
        "Code",
        "User",
        "workspaceStorage",
      ),
    );
    roots.push(
      join(
        home,
        "Library",
        "Application Support",
        "Cursor",
        "User",
        "workspaceStorage",
      ),
    );
  } else {
    roots.push(join(xdgConfig, "Code", "User", "workspaceStorage"));
    roots.push(join(xdgConfig, "Cursor", "User", "workspaceStorage"));
  }
  return roots.filter((p) => existsSync(p));
}

function scanAdtWorkspace(
  adtWorkspacePath: string,
  sourceLabel: string,
): GuiDestination[] {
  const cacheRoot = join(adtWorkspacePath, SEMANTIC_CACHE_SUFFIX);
  if (!existsSync(cacheRoot)) {
    return [];
  }
  const found: GuiDestination[] = [];
  for (const entry of readdirSync(cacheRoot, { withFileTypes: true })) {
    if (!entry.isDirectory()) {
      continue;
    }
    const propertiesPath = join(cacheRoot, entry.name, DESTINATION_FILE);
    if (!existsSync(propertiesPath)) {
      continue;
    }
    const id = readDestinationId(propertiesPath) ?? entry.name;
    found.push({
      id,
      workspaceFolderUri: buildAbapWorkspaceFolderUri(id),
      adtWorkspacePath,
      propertiesPath,
    });
  }
  return found;
}

function scoreBundle(destinations: GuiDestination[]): number {
  if (destinations.length === 0) {
    return 0;
  }
  let newest = 0;
  for (const d of destinations) {
    try {
      newest = Math.max(newest, statSync(d.propertiesPath).mtimeMs);
    } catch {
      /* ignore */
    }
  }
  return destinations.length * 1_000_000_000_000 + newest;
}

/**
 * Discover SAP ADT destinations persisted by the VS Code / Cursor GUI
 * (Eclipse semantic cache under workspaceStorage).
 */
export function discoverGuiDestinations(): GuiImportBundle | undefined {
  const byWorkspace = new Map<
    string,
    { destinations: GuiDestination[]; sources: Set<string> }
  >();

  for (const storageRoot of listWorkspaceStorageRoots()) {
    let entries: { name: string; isDirectory: () => boolean }[];
    try {
      entries = readdirSync(storageRoot, { withFileTypes: true });
    } catch {
      continue;
    }
    const sourceLabel = storageRoot.includes(`${join("Cursor", "User")}`)
      ? "cursor"
      : "vscode";
    for (const entry of entries) {
      if (!entry.isDirectory()) {
        continue;
      }
      const adtWorkspacePath = join(
        storageRoot,
        entry.name,
        ADT_WORKSPACE_SUFFIX,
      );
      if (!existsSync(adtWorkspacePath)) {
        continue;
      }
      const destinations = scanAdtWorkspace(adtWorkspacePath, sourceLabel);
      if (destinations.length === 0) {
        continue;
      }
      const existing = byWorkspace.get(adtWorkspacePath);
      if (existing) {
        for (const d of destinations) {
          if (!existing.destinations.some((x) => x.id === d.id)) {
            existing.destinations.push(d);
          }
        }
        existing.sources.add(sourceLabel);
      } else {
        byWorkspace.set(adtWorkspacePath, {
          destinations: [...destinations],
          sources: new Set([sourceLabel]),
        });
      }
    }
  }

  for (const eclipseRoot of eclipseWorkspaceCandidates()) {
    const destinations = scanAdtWorkspace(eclipseRoot, "eclipse");
    if (destinations.length === 0) {
      continue;
    }
    const existing = byWorkspace.get(eclipseRoot);
    if (existing) {
      for (const d of destinations) {
        if (!existing.destinations.some((x) => x.id === d.id)) {
          existing.destinations.push(d);
        }
      }
      existing.sources.add("eclipse");
    } else {
      byWorkspace.set(eclipseRoot, {
        destinations: [...destinations],
        sources: new Set(["eclipse"]),
      });
    }
  }

  let best: GuiImportBundle | undefined;
  let bestScore = 0;
  for (const [adtWorkspacePath, { destinations, sources }] of byWorkspace) {
    const score = scoreBundle(destinations);
    if (score > bestScore) {
      bestScore = score;
      best = {
        adtWorkspacePath,
        destinations: destinations.sort((a, b) => a.id.localeCompare(b.id)),
        sources: [...sources],
      };
    }
  }
  return best;
}

export function resolveDestinationImport(
  workspace: string,
  importFrom: DestinationImportMode,
  explicitWorkspace: boolean,
): {
  workspace: string;
  workspaceFolderUris: string[];
  imported: GuiDestination[];
  bundle?: GuiImportBundle;
  importSource?: string;
  destinationsStorePath?: string;
} {
  if (importFrom === "none") {
    return { workspace, workspaceFolderUris: [], imported: [] };
  }

  if (importFrom === "adtls" || importFrom === "auto") {
    const adtls = discoverAdtlsDestinations(workspace);
    if (adtls && adtls.destinations.length > 0) {
      const dataWorkspace = resolveAdtLscDataWorkspace(
        workspace,
        explicitWorkspace,
      );
      const store = loadAdtlsDestinationsStore();
      if (store) {
        materializeAdtlsDestinations(dataWorkspace, store);
      }
      return {
        workspace: dataWorkspace,
        workspaceFolderUris: adtls.destinations.map(
          (d) => d.workspaceFolderUri,
        ),
        imported: adtls.destinations.map((d) => ({
          ...d,
          adtWorkspacePath: dataWorkspace,
        })),
        importSource: adtlsImportSourceLabel(),
        destinationsStorePath: adtls.storePath,
      };
    }
    if (importFrom === "adtls") {
      return { workspace, workspaceFolderUris: [], imported: [] };
    }
  }

  if (importFrom === "gui" || importFrom === "auto") {
    const bundle = discoverGuiDestinations();
    if (bundle && bundle.destinations.length > 0) {
      return {
        workspace: explicitWorkspace ? workspace : bundle.adtWorkspacePath,
        workspaceFolderUris: bundle.destinations.map(
          (d) => d.workspaceFolderUri,
        ),
        imported: bundle.destinations,
        bundle,
        importSource: bundle.sources.join("+"),
      };
    }
    if (importFrom === "gui") {
      return { workspace, workspaceFolderUris: [], imported: [], bundle };
    }
  }

  const materialized = materializeOpenAdtDestinations(workspace);
  if (materialized.length === 0) {
    return { workspace, workspaceFolderUris: [], imported: [] };
  }

  return {
    workspace,
    workspaceFolderUris: materialized.map((d) => d.workspaceFolderUri),
    imported: materialized,
    importSource: openAdtImportSourceLabel(),
  };
}

/** Avoid `-data` = VS Code adtWorkspace while VS Code may hold the same Eclipse workspace. */
function resolveAdtLscDataWorkspace(
  workspace: string,
  explicitWorkspace: boolean,
): string {
  if (explicitWorkspace && !isVsCodeAdtWorkspacePath(workspace)) {
    return workspace;
  }
  if (isVsCodeAdtWorkspacePath(workspace)) {
    return DEFAULT_WORKSPACE;
  }
  return workspace === DEFAULT_WORKSPACE || !explicitWorkspace
    ? DEFAULT_WORKSPACE
    : workspace;
}

/** @deprecated use resolveDestinationImport */
export function resolveServeFromGuiImport(
  workspace: string,
  importFrom: DestinationImportMode,
  explicitWorkspace: boolean,
) {
  return resolveDestinationImport(workspace, importFrom, explicitWorkspace);
}
