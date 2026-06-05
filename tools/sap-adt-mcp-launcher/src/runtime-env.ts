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
    const parsed = Bun.TOML.parse(readFileSync(LOCAL_CONFIG, "utf8")) as {
      runtime?: { jco_native_dir?: string; sapcrypto?: string };
      jco_native_dir?: string;
      sapcrypto?: string;
    };
    const runtime = parsed.runtime ?? parsed;
    return {
      jcoNativeDir: readTomlString(runtime.jco_native_dir),
      sapcrypto: readTomlString(runtime.sapcrypto),
    };
  } catch {
    return {};
  }
}

function readTomlString(value: string | undefined): string | undefined {
  const trimmed = value?.trim();
  return trimmed || undefined;
}

export function buildAdtLscSpawnRuntime(
  paths: OpenAdtRuntimePaths = loadOpenAdtRuntimePaths(),
): AdtLscSpawnRuntime {
  const env = ensureMinimalProcessEnv({ ...process.env });
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
      env.SNC_LIB = paths.sapcrypto;
    }
    libraryPathEntries.push(sapDir);
    jvmArgs.push(`-Djco.middleware.snc_lib=${paths.sapcrypto}`);
  }

  if (libraryPathEntries.length > 0) {
    const sep = process.platform === "win32" ? ";" : ":";
    jvmArgs.push(`-Djava.library.path=${libraryPathEntries.join(sep)}`);
  }

  if (!env.SECUDIR?.trim()) {
    for (const candidate of secudirCandidates()) {
      if (isSecureLoginSecudir(candidate)) {
        env.SECUDIR = candidate;
        break;
      }
    }
  }

  return { env, jvmArgs };
}

/** ~/.openadt/sec holds HTTP CA PEMs — not SAP Secure Login SECUDIR. */
export function isSecureLoginSecudir(candidate: string): boolean {
  if (!existsSync(candidate)) {
    return false;
  }
  const normalized = candidate.replace(/\\/g, "/").toLowerCase();
  if (normalized.endsWith("/.openadt/sec")) {
    return false;
  }
  return true;
}

function secudirCandidates(): string[] {
  const home = homedir();
  const appData = process.env.APPDATA;
  const candidates: string[] = [];
  if (appData) {
    candidates.push(join(appData, "SAP", "Common"));
  }
  candidates.push(join(home, "AppData", "Roaming", "SAP", "Common"));
  candidates.push("C:\\Program Files\\SAP\\FrontEnd\\SecureLogin\\lib");
  candidates.push(join(home, ".openadt", "sec"));
  return candidates;
}

/** Cursor agent CLI passes a stripped env; fill Windows profile dirs for SECUDIR/JCo. */
export function ensureMinimalProcessEnv(
  env: NodeJS.ProcessEnv,
): NodeJS.ProcessEnv {
  if (process.platform !== "win32") {
    return env;
  }
  const home = homedir();
  const findKey = (key: string): string | undefined =>
    Object.keys(env).find((k) => k.toUpperCase() === key.toUpperCase());
  const readVal = (key: string): string | undefined => {
    const k = findKey(key);
    return k ? env[k] : undefined;
  };
  const writeVal = (key: string, value: string): void => {
    const existing = findKey(key) ?? key;
    env[existing] = value;
  };

  if (!readVal("USERPROFILE")?.trim()) {
    writeVal("USERPROFILE", home);
  }
  if (!readVal("HOME")?.trim()) {
    writeVal("HOME", home);
  }
  if (!readVal("SystemRoot")?.trim() && !readVal("WINDIR")?.trim()) {
    writeVal("SystemRoot", "C:\\Windows");
  }
  if (!readVal("APPDATA")?.trim()) {
    writeVal("APPDATA", join(home, "AppData", "Roaming"));
  }
  let localAppData = readVal("LOCALAPPDATA")?.trim();
  if (!localAppData) {
    localAppData = join(home, "AppData", "Local");
    writeVal("LOCALAPPDATA", localAppData);
  }
  let temp = readVal("TEMP")?.trim();
  if (!temp) {
    temp = join(localAppData, "Temp");
    writeVal("TEMP", temp);
  }
  if (!readVal("TMP")?.trim()) {
    writeVal("TMP", temp);
  }
  return env;
}

function prependPath(env: NodeJS.ProcessEnv, dir: string): void {
  const sep = process.platform === "win32" ? ";" : ":";
  const pathKey =
    Object.keys(env).find((k) => k.toUpperCase() === "PATH") ?? "PATH";
  const current = env[pathKey] ?? "";
  if (
    current
      .split(sep)
      .some((entry) => entry.toLowerCase() === dir.toLowerCase())
  ) {
    return;
  }
  env[pathKey] = current ? `${dir}${sep}${current}` : dir;
}

export function isVsCodeAdtWorkspacePath(workspace: string): boolean {
  const normalized = workspace.replace(/\\/g, "/").toLowerCase();
  return (
    normalized.includes("workspacestorage") &&
    normalized.includes("sapse.adt-vscode/adtworkspace")
  );
}
