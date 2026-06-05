import {
  existsSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  writeFileSync,
} from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";
import {
  buildAbapWorkspaceFolderUri,
  DESTINATION_FILE,
  type GuiDestination,
  SEMANTIC_CACHE_SUFFIX,
} from "./gui-import.ts";

function openAdtDir(): string {
  return process.env.OPENADT_HOME ?? join(homedir(), ".openadt");
}

export type OpenAdtDestinationEntry = {
  alias: string;
  systemId: string;
  client: string;
  user: string;
  language: string;
  description?: string;
  jco: Record<string, string>;
};

/** ADT LS destination id: SYSTEM_CLIENT_USER_LANG (same as Eclipse ADT). */
export function buildAdtDestinationId(entry: OpenAdtDestinationEntry): string {
  return `${entry.systemId}_${entry.client}_${entry.user}_${entry.language}`;
}

export function destinationPropertiesContent(
  entry: OpenAdtDestinationEntry,
): string {
  const id = buildAdtDestinationId(entry);
  const lines: string[] = [
    `id=${id}`,
    `systemId=${entry.systemId}`,
    `client=${entry.client}`,
    `user=${entry.user}`,
    `language=${entry.language}`,
  ];
  if (entry.description) {
    lines.push(`description=${entry.description}`);
  }
  const jco = entry.jco;
  if (jco.mshost) {
    lines.push(`messageServer=${jco.mshost}`);
  }
  if (jco.msserv) {
    lines.push(`messageServerService=${jco.msserv}`);
  }
  if (jco.ashost) {
    lines.push(`applicationServerHost=${jco.ashost}`);
  }
  if (jco.sysnr) {
    lines.push(`systemNumber=${jco.sysnr}`);
  }
  if (jco.group) {
    lines.push(`group=${jco.group}`);
  }
  if (jco.snc_partnername) {
    lines.push(`partnerName=${jco.snc_partnername}`);
  }
  if (jco.snc_sso === "1") {
    lines.push("SSOEnabled=1");
  }
  if (jco.snc_qop) {
    lines.push(`SNCType=${jco.snc_qop}`);
  } else if (jco.snc_mode) {
    lines.push(`SNCType=${jco.snc_mode}`);
  }
  lines.push("");
  return lines.join("\n");
}

function collectTomlFiles(): string[] {
  const files = new Set<string>();
  const root = openAdtDir();
  const configPath = join(root, "config.toml");
  if (existsSync(configPath)) {
    files.add(configPath);
    try {
      const config = Bun.TOML.parse(readFileSync(configPath, "utf8")) as {
        merge?: { includes?: string[] };
      };
      for (const pattern of config.merge?.includes ?? []) {
        if (!pattern.includes("destinations")) {
          continue;
        }
        const dir = join(root, pattern.replace(/\/\*.*$/, ""));
        if (!existsSync(dir)) {
          continue;
        }
        for (const name of readdirSync(dir)) {
          if (name.endsWith(".toml")) {
            files.add(join(dir, name));
          }
        }
      }
    } catch {
      /* fall through to destinations dir */
    }
  }
  const destDir = join(root, "destinations");
  if (existsSync(destDir)) {
    for (const name of readdirSync(destDir)) {
      if (name.endsWith(".toml")) {
        files.add(join(destDir, name));
      }
    }
  }
  return [...files];
}

export function loadOpenAdtDestinationEntries(): OpenAdtDestinationEntry[] {
  const byAlias = new Map<string, OpenAdtDestinationEntry>();

  for (const file of collectTomlFiles()) {
    let parsed: Record<string, unknown>;
    try {
      parsed = Bun.TOML.parse(readFileSync(file, "utf8")) as Record<
        string,
        unknown
      >;
    } catch {
      continue;
    }
    const destinations = parsed.destinations as
      | Record<string, Record<string, unknown>>
      | undefined;
    if (!destinations) {
      continue;
    }
    for (const [alias, raw] of Object.entries(destinations)) {
      if (!raw || typeof raw !== "object") {
        continue;
      }
      const systemId = String(raw.system_id ?? raw.alias ?? alias).trim();
      const client = String(raw.client ?? "").trim();
      const user = String(raw.user ?? "").trim();
      const language = String(raw.language ?? "EN").trim();
      if (!systemId || !client || !user) {
        continue;
      }
      const jco = (raw.jco as Record<string, string> | undefined) ?? {};
      if (!jco.mshost && !jco.ashost) {
        continue;
      }
      byAlias.set(alias, {
        alias,
        systemId,
        client,
        user,
        language,
        description:
          typeof raw.description === "string" ? raw.description : undefined,
        jco: Object.fromEntries(
          Object.entries(jco).map(([k, v]) => [k, String(v)]),
        ),
      });
    }
  }

  return [...byAlias.values()].sort((a, b) => a.alias.localeCompare(b.alias));
}

/**
 * Write Eclipse semantic cache under workspace and return MCP registration entries.
 */
export function materializeOpenAdtDestinations(
  workspace: string,
): GuiDestination[] {
  const entries = loadOpenAdtDestinationEntries();
  const cacheRoot = join(workspace, SEMANTIC_CACHE_SUFFIX);
  const materialized: GuiDestination[] = [];

  for (const entry of entries) {
    const id = buildAdtDestinationId(entry);
    const projectDir = join(cacheRoot, id);
    const propertiesPath = join(projectDir, DESTINATION_FILE);
    mkdirSync(projectDir, { recursive: true });
    writeFileSync(propertiesPath, destinationPropertiesContent(entry), "utf8");
    materialized.push({
      id,
      workspaceFolderUri: buildAbapWorkspaceFolderUri(id),
      adtWorkspacePath: workspace,
      propertiesPath,
    });
  }

  return materialized;
}

export function openAdtImportSourceLabel(): string {
  return "openadt";
}
