import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { homedir } from "node:os";
import { isAbsolute, join, resolve } from "node:path";
import { pathToFileURL } from "node:url";
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
import { isVsCodeAdtWorkspacePath, type WorkspacePath } from "./runtime-env.ts";
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

/** Caller-supplied input for resolving which SAP ADT destinations to import. */
export class DestinationImportRequest {
  constructor(
    readonly source: DestinationImportMode,
    readonly workspace: string,
    readonly explicitWorkspace: boolean,
  ) {}
}

export class DestinationImportResult {
  workspace: string;
  workspaceFolderUris: string[];
  imported: GuiDestination[];
  bundle?: GuiImportBundle;
  importSource?: string;
  destinationsStorePath?: string;
  fileUris: string[];

  constructor(init: {
    workspace: string;
    workspaceFolderUris: string[];
    imported: GuiDestination[];
    bundle?: GuiImportBundle;
    importSource?: string;
    destinationsStorePath?: string;
    fileUris?: string[];
  }) {
    this.workspace = init.workspace;
    this.workspaceFolderUris = init.workspaceFolderUris;
    this.imported = init.imported;
    this.bundle = init.bundle;
    this.importSource = init.importSource;
    this.destinationsStorePath = init.destinationsStorePath;
    this.fileUris = init.fileUris ?? [];
  }
}

/** Build the `abap:/<destinationId>` folder URI the ADT VS Code extension uses. */
export function buildAbapWorkspaceFolderUri(destinationId: string): string {
  const trimmed = destinationId.trim();
  if (!trimmed) {
    throw new Error("destination id is empty");
  }
  return `${ABAP_WORKSPACE_SCHEME}:/${trimmed}`;
}

/** file:// URIs for materialized `.destination.properties` (adtLs/destinations/initializeService). */
export function destinationFileUris(destinations: GuiDestination[]): string[] {
  return destinations.map(
    (d) =>
      pathToFileURL(
        isAbsolute(d.propertiesPath)
          ? d.propertiesPath
          : resolve(d.propertiesPath),
      ).href,
  );
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

function scanAdtWorkspace(adtWorkspacePath: string): GuiDestination[] {
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

type BundleEntry = {
  destinations: GuiDestination[];
  sources: Set<string>;
};

function mergeIntoBundle(
  entry: BundleEntry,
  destinations: GuiDestination[],
  sourceLabel: string,
): void {
  for (const d of destinations) {
    if (!entry.destinations.some((x) => x.id === d.id)) {
      entry.destinations.push(d);
    }
  }
  entry.sources.add(sourceLabel);
}

function startBundle(
  destinations: GuiDestination[],
  sourceLabel: string,
): BundleEntry {
  return { destinations: [...destinations], sources: new Set([sourceLabel]) };
}

function collectFromStorageRoots(byWorkspace: Map<string, BundleEntry>): void {
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
      const destinations = scanAdtWorkspace(adtWorkspacePath);
      if (destinations.length === 0) {
        continue;
      }
      const existing = byWorkspace.get(adtWorkspacePath);
      if (existing) {
        mergeIntoBundle(existing, destinations, sourceLabel);
      } else {
        byWorkspace.set(
          adtWorkspacePath,
          startBundle(destinations, sourceLabel),
        );
      }
    }
  }
}

function collectFromEclipseWorkspaces(
  byWorkspace: Map<string, BundleEntry>,
): void {
  for (const eclipseRoot of eclipseWorkspaceCandidates()) {
    const destinations = scanAdtWorkspace(eclipseRoot);
    if (destinations.length === 0) {
      continue;
    }
    const existing = byWorkspace.get(eclipseRoot);
    if (existing) {
      mergeIntoBundle(existing, destinations, "eclipse");
    } else {
      byWorkspace.set(eclipseRoot, startBundle(destinations, "eclipse"));
    }
  }
}

function pickBestBundle(
  byWorkspace: Map<string, BundleEntry>,
): GuiImportBundle | undefined {
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

/**
 * Discover SAP ADT destinations persisted by the VS Code / Cursor GUI
 * (Eclipse semantic cache under workspaceStorage).
 */
export function discoverGuiDestinations(): GuiImportBundle | undefined {
  const byWorkspace = new Map<string, BundleEntry>();
  collectFromStorageRoots(byWorkspace);
  collectFromEclipseWorkspaces(byWorkspace);
  return pickBestBundle(byWorkspace);
}

export function resolveDestinationImport(
  workspace: string,
  importFrom: DestinationImportMode,
  explicitWorkspace: boolean,
): DestinationImportResult {
  return resolveDestinationImportByRequest(
    new DestinationImportRequest(importFrom, workspace, explicitWorkspace),
  );
}

function resolveDestinationImportByRequest(
  req: DestinationImportRequest,
): DestinationImportResult {
  switch (req.source) {
    case "none":
      return resolveNoneImport(req);
    case "adtls":
      return resolveAdtlsImport(req);
    case "gui":
      return resolveGuiImport(req);
    case "auto":
      return resolveAutoImport(req);
    case "openadt":
      return importFromOpenAdt(req);
  }
}

function resolveNoneImport(
  req: DestinationImportRequest,
): DestinationImportResult {
  return emptyImport(req.workspace);
}

function resolveAdtlsImport(
  req: DestinationImportRequest,
): DestinationImportResult {
  return importFromAdtls(req) ?? emptyImport(req.workspace);
}

function resolveGuiImport(
  req: DestinationImportRequest,
): DestinationImportResult {
  return importFromGui(req);
}

function resolveAutoImport(
  req: DestinationImportRequest,
): DestinationImportResult {
  const adtls = importFromAdtls(req);
  if (adtls) {
    return adtls;
  }
  const gui = importFromGui(req);
  if (gui.imported.length > 0) {
    return gui;
  }
  return importFromOpenAdt(req);
}

function emptyImport(workspace: string): DestinationImportResult {
  return new DestinationImportResult({
    workspace,
    workspaceFolderUris: [],
    imported: [],
    fileUris: [],
  });
}

function importFromAdtls(
  req: DestinationImportRequest,
): DestinationImportResult | undefined {
  const adtls = discoverAdtlsDestinations(req.workspace);
  if (!adtls || adtls.destinations.length === 0) {
    return undefined;
  }
  const dataWorkspace = resolveAdtLscDataWorkspace(req);
  const store = loadAdtlsDestinationsStore();
  const materialized = store
    ? materializeAdtlsDestinations(dataWorkspace, store)
    : [];
  if (materialized.length === 0) {
    return undefined;
  }
  return new DestinationImportResult({
    workspace: dataWorkspace,
    workspaceFolderUris: materialized.map((d) => d.workspaceFolderUri),
    imported: materialized,
    importSource: adtlsImportSourceLabel(),
    destinationsStorePath: adtls.storePath,
    fileUris: destinationFileUris(materialized),
  });
}

function importFromGui(req: DestinationImportRequest): DestinationImportResult {
  const bundle = discoverGuiDestinations();
  if (!bundle || bundle.destinations.length === 0) {
    return new DestinationImportResult({
      workspace: req.workspace,
      workspaceFolderUris: [],
      imported: [],
      bundle,
      fileUris: [],
    });
  }
  return new DestinationImportResult({
    workspace: req.explicitWorkspace ? req.workspace : bundle.adtWorkspacePath,
    workspaceFolderUris: bundle.destinations.map((d) => d.workspaceFolderUri),
    imported: bundle.destinations,
    bundle,
    importSource: bundle.sources.join("+"),
    fileUris: destinationFileUris(bundle.destinations),
  });
}

function importFromOpenAdt(
  req: DestinationImportRequest,
): DestinationImportResult {
  const materialized = materializeOpenAdtDestinations(req.workspace);
  if (materialized.length === 0) {
    return emptyImport(req.workspace);
  }
  return new DestinationImportResult({
    workspace: req.workspace,
    workspaceFolderUris: materialized.map((d) => d.workspaceFolderUri),
    imported: materialized,
    importSource: openAdtImportSourceLabel(),
    fileUris: destinationFileUris(materialized),
  });
}

/** Avoid `-data` = VS Code adtWorkspace while VS Code may hold the same Eclipse workspace. */
function resolveAdtLscDataWorkspace(req: DestinationImportRequest): string {
  const { workspace, explicitWorkspace } = req;
  if (
    explicitWorkspace &&
    !isVsCodeAdtWorkspacePath(workspace as WorkspacePath)
  ) {
    return workspace;
  }
  if (isVsCodeAdtWorkspacePath(workspace as WorkspacePath)) {
    return DEFAULT_WORKSPACE;
  }
  return workspace === DEFAULT_WORKSPACE || !explicitWorkspace
    ? DEFAULT_WORKSPACE
    : workspace;
}
