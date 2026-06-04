import { existsSync, readFileSync } from "node:fs";
import { homedir } from "node:os";
import { dirname, join } from "node:path";

export type OpenAdtRuntimePaths = {
  jcoNativeDir?: string;
  sapcrypto?: string;
};

export type AdtLscSpawnRuntime = {
  env: NodeJS.ProcessEnv;
  jvmArgs: string[];
};

const LOCAL_CONFIG = join(homedir(), ".openadt", "local.openadt.toml");

/** Minimal TOML field read (avoid pulling full parser into launcher). */
export function loadOpenAdtRuntimePaths(): OpenAdtRuntimePaths {
  if (!existsSync(LOCAL_CONFIG)) {
    return {};
  }
  try {
    const text = readFileSync(LOCAL_CONFIG, "utf8");
    return {
      jcoNativeDir: readTomlString(text, "jco_native_dir"),
      sapcrypto: readTomlString(text, "sapcrypto"),
    };
  } catch {
    return {};
  }
}

function readTomlString(text: string, key: string): string | undefined {
  const match = text.match(new RegExp(`^\\s*${key}\\s*=\\s*"([^"]*)"`, "m"));
  return match?.[1]?.trim() || undefined;
}

export function buildAdtLscSpawnRuntime(
  paths: OpenAdtRuntimePaths = loadOpenAdtRuntimePaths(),
): AdtLscSpawnRuntime {
  const env = { ...process.env };
  const jvmArgs: string[] = [];
  const libraryPathEntries: string[] = [];

  if (paths.jcoNativeDir) {
    if (existsSync(paths.jcoNativeDir)) {
      prependPath(env, paths.jcoNativeDir);
    }
    libraryPathEntries.push(paths.jcoNativeDir);
  }
  if (paths.sapcrypto) {
    const sapDir = dirname(paths.sapcrypto);
    if (existsSync(paths.sapcrypto)) {
      prependPath(env, sapDir);
    }
    libraryPathEntries.push(sapDir);
    jvmArgs.push(`-Djco.middleware.snc_lib=${paths.sapcrypto}`);
  }

  if (libraryPathEntries.length > 0) {
    jvmArgs.push(`-Djava.library.path=${libraryPathEntries.join(";")}`);
  }

  if (!env.SECUDIR?.trim()) {
    for (const candidate of secudirCandidates()) {
      if (existsSync(candidate)) {
        env.SECUDIR = candidate;
        break;
      }
    }
  }

  return { env, jvmArgs };
}

function secudirCandidates(): string[] {
  const home = homedir();
  const appData = process.env.APPDATA;
  const candidates = [join(home, ".openadt", "sec")];
  if (appData) {
    candidates.push(join(appData, "SAP", "Common"));
  }
  candidates.push(
    "C:\\Program Files\\SAP\\FrontEnd\\SecureLogin\\lib",
    join(home, "AppData", "Roaming", "SAP", "Common"),
  );
  return candidates;
}

function prependPath(env: NodeJS.ProcessEnv, dir: string): void {
  const sep = process.platform === "win32" ? ";" : ":";
  const current = env.PATH ?? env.Path ?? "";
  if (
    current
      .split(sep)
      .some((entry) => entry.toLowerCase() === dir.toLowerCase())
  ) {
    return;
  }
  env.PATH = current ? `${dir}${sep}${current}` : dir;
}

export function isVsCodeAdtWorkspacePath(workspace: string): boolean {
  const normalized = workspace.replace(/\\/g, "/").toLowerCase();
  return (
    normalized.includes("workspacestorage") &&
    normalized.includes("sapse.adt-vscode/adtworkspace")
  );
}
