import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";
import {
  buildAbapWorkspaceFolderUri,
  DESTINATION_FILE,
  type GuiDestination,
  SEMANTIC_CACHE_SUFFIX,
} from "./gui-import.ts";

export type AdtlsDestinationRecord = {
  id: string;
  protocol?: string;
  properties?: Record<string, string>;
};

export type AdtlsDestinationsStore = {
  formatVersion?: string;
  destinations?: AdtlsDestinationRecord[];
};

export function adtlsHomeDir(): string {
  return process.env.ADTLS_HOME ?? join(homedir(), ".adtls");
}

/** Canonical ADT language server destination store (VS Code ADT GUI). */
export function adtlsDestinationsStorePath(): string {
  return join(adtlsHomeDir(), "destinations.json");
}

export function loadAdtlsDestinationsStore():
  | AdtlsDestinationsStore
  | undefined {
  const path = adtlsDestinationsStorePath();
  if (!existsSync(path)) {
    return undefined;
  }
  try {
    return JSON.parse(readFileSync(path, "utf8")) as AdtlsDestinationsStore;
  } catch {
    return undefined;
  }
}

export function destinationsFromAdtlsStore(
  store: AdtlsDestinationsStore,
  workspace: string,
): GuiDestination[] {
  const path = adtlsDestinationsStorePath();
  const list = store.destinations ?? [];
  const imported: GuiDestination[] = [];

  for (const entry of list) {
    const id = entry.id?.trim();
    if (!id) {
      continue;
    }
    imported.push({
      id,
      workspaceFolderUri: buildAbapWorkspaceFolderUri(id),
      adtWorkspacePath: workspace,
      propertiesPath: path,
    });
  }

  return imported.sort((a, b) => a.id.localeCompare(b.id));
}

export function discoverAdtlsDestinations(workspace: string):
  | {
      /** ADT LS store directory (~/.adtls), not the JSON file path. */
      storePath: string;
      destinations: GuiDestination[];
    }
  | undefined {
  const store = loadAdtlsDestinationsStore();
  if (!store?.destinations?.length) {
    return undefined;
  }
  const destinations = destinationsFromAdtlsStore(store, workspace);
  if (destinations.length === 0) {
    return undefined;
  }
  return { storePath: adtlsHomeDir(), destinations };
}

export function adtlsImportSourceLabel(): string {
  return "adtls";
}

/** Write ~/.adtls entries into adt-lsc workspace semantic cache (Eclipse .destination.properties). */
export function materializeAdtlsDestinations(
  workspace: string,
  store: AdtlsDestinationsStore = loadAdtlsDestinationsStore() ?? {},
): GuiDestination[] {
  const cacheRoot = join(workspace, SEMANTIC_CACHE_SUFFIX);
  const materialized: GuiDestination[] = [];

  for (const entry of store.destinations ?? []) {
    const id = entry.id?.trim();
    if (!id) {
      continue;
    }
    const props = entry.properties ?? {};
    const projectDir = join(cacheRoot, id);
    const propertiesPath = join(projectDir, DESTINATION_FILE);
    mkdirSync(projectDir, { recursive: true });
    writeFileSync(
      propertiesPath,
      adtlsDestinationPropertiesContent(id, props),
      "utf8",
    );
    materialized.push({
      id,
      workspaceFolderUri: buildAbapWorkspaceFolderUri(id),
      adtWorkspacePath: workspace,
      propertiesPath,
    });
  }

  return materialized.sort((a, b) => a.id.localeCompare(b.id));
}

export function adtlsDestinationPropertiesContent(
  id: string,
  props: Record<string, string>,
): string {
  const lines: string[] = [`id=${id}`];
  const copyKeys: Array<[adtlsKey: string, propKey: string]> = [
    ["systemId", "systemId"],
    ["client", "client"],
    ["user", "user"],
    ["language", "language"],
    ["messageServer", "messageServer"],
    ["messageServerPort", "messageServerService"],
    ["group", "group"],
    ["partnerName", "partnerName"],
    ["sncType", "SNCType"],
  ];
  for (const [from, to] of copyKeys) {
    const value = props[from]?.trim();
    if (value) {
      lines.push(`${to}=${value}`);
    }
  }
  if (props.ssoEnabled?.toLowerCase() === "true") {
    lines.push("SSOEnabled=1");
  }
  lines.push("");
  return lines.join("\n");
}
