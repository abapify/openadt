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

export type GetEnvStringOptions = {
  default?: string;
  required?: boolean;
};

export type GetEnvIntOptions = {
  min?: number;
  max?: number;
};

export type GetEnvPathOptions = {
  mustExist?: boolean;
};

/** Typed accessor over `NodeJS.ProcessEnv` (Windows keys are case-insensitive). */
export class Env {
  private readonly lookup: Map<string, string>;

  static fromProcess(): Env {
    return new Env({ ...process.env });
  }

  constructor(env: NodeJS.ProcessEnv) {
    this.lookup = new Map();
    for (const key of Object.keys(env)) {
      this.lookup.set(key.toUpperCase(), key);
    }
    this.env = env;
  }

  private readonly env: NodeJS.ProcessEnv;

  private existingKey(name: string): string {
    return this.lookup.get(name.toUpperCase()) ?? name;
  }

  hasNonEmpty(name: string): boolean {
    return Boolean(this.getTrimmed(name));
  }

  getTrimmed(name: string): string | undefined {
    const key = this.lookup.get(name.toUpperCase());
    const value = key === undefined ? undefined : this.env[key];
    const trimmed = typeof value === "string" ? value.trim() : undefined;
    return trimmed || undefined;
  }

  set(name: string, value: string): void {
    this.env[this.existingKey(name)] = value;
  }

  /** Read a string env var. `default` applies when unset or blank; `required` throws otherwise. */
  string(name: string, opts: GetEnvStringOptions = {}): string | undefined {
    const raw = this.getTrimmed(name);
    if (raw) {
      return raw;
    }
    if (opts.default !== undefined) {
      return opts.default;
    }
    if (opts.required) {
      throw new Error(`Missing required env var ${name}`);
    }
    return undefined;
  }

  /** Parse an env var as an integer within `[min, max]`. Returns undefined when unset/blank. */
  integer(name: string, opts: GetEnvIntOptions = {}): number | undefined {
    const raw = this.getTrimmed(name);
    if (!raw) {
      return undefined;
    }
    const value = Number(raw);
    if (!Number.isInteger(value)) {
      throw new Error(`Env ${name}=${raw} is not an integer`);
    }
    if (opts.min !== undefined && value < opts.min) {
      throw new Error(`Env ${name}=${value} below min ${opts.min}`);
    }
    if (opts.max !== undefined && value > opts.max) {
      throw new Error(`Env ${name}=${value} above max ${opts.max}`);
    }
    return value;
  }

  /** Read a filesystem path env var; reject when `mustExist` and the path is absent. */
  path(name: string, opts: GetEnvPathOptions = {}): string | undefined {
    const raw = this.getTrimmed(name);
    if (!raw) {
      return undefined;
    }
    if (opts.mustExist && !existsSync(raw)) {
      return undefined;
    }
    return raw;
  }
}

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
    const view = Env.fromProcess();
    for (const candidate of secudirCandidates(view)) {
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

function secudirCandidates(env: Env): string[] {
  const home = homedir();
  const appData = env.string("APPDATA") ?? "";
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
  const view = new Env(env);
  const home = homedir();
  ensureHomeProfile(view, home);
  ensureSystemRoot(view);
  ensureAppData(view, home);
  const temp = ensureTempDir(view, home);
  ensureTmpMirror(view, temp);
  return env;
}

function ensureHomeProfile(view: Env, home: string): void {
  if (!view.hasNonEmpty("USERPROFILE")) {
    view.set("USERPROFILE", home);
  }
  if (!view.hasNonEmpty("HOME")) {
    view.set("HOME", home);
  }
}

function ensureSystemRoot(view: Env): void {
  if (view.hasNonEmpty("SystemRoot") || view.hasNonEmpty("WINDIR")) {
    return;
  }
  view.set("SystemRoot", "C:\\Windows");
}

function ensureAppData(view: Env, home: string): void {
  if (!view.hasNonEmpty("APPDATA")) {
    view.set("APPDATA", join(home, "AppData", "Roaming"));
  }
  if (!view.hasNonEmpty("LOCALAPPDATA")) {
    view.set("LOCALAPPDATA", join(home, "AppData", "Local"));
  }
}

function ensureTempDir(view: Env, home: string): string {
  const existing = view.getTrimmed("TEMP");
  if (existing) {
    return existing;
  }
  const localAppData =
    view.getTrimmed("LOCALAPPDATA") ?? join(home, "AppData", "Local");
  const temp = join(localAppData, "Temp");
  view.set("TEMP", temp);
  return temp;
}

function ensureTmpMirror(view: Env, temp: string): void {
  if (view.hasNonEmpty("TMP")) {
    return;
  }
  view.set("TMP", temp);
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
