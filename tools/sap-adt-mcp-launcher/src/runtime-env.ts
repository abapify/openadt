import { existsSync, readFileSync } from "node:fs";
import { homedir } from "node:os";
import { dirname, join } from "node:path";
import { Env, envVar } from "./env.ts";
import type {
  EnvVarName,
  GetIntOpts,
  GetPathOpts,
  GetStringOpts,
  SetEnvOpts,
  WindowsEnvDefaults,
  WorkspacePath,
} from "./env.ts";

export type OpenAdtRuntimePaths = {
  jcoNativeDir?: string;
  sapcrypto?: string;
};

export type AdtLscSpawnRuntime = {
  env: NodeJS.ProcessEnv;
  jvmArgs: string[];
};

export { Env, envVar };
export type {
  EnvVarName,
  GetIntOpts,
  GetPathOpts,
  GetStringOpts,
  SetEnvOpts,
  WindowsEnvDefaults,
  WorkspacePath,
};

const LOCAL_CONFIG = join(homedir(), ".openadt", "local.openadt.toml");

/** Minimal TOML field read (avoid pulling full parser into launcher). */
export function loadOpenAdtRuntimePaths(opts?: {
  configPath?: string;
}): OpenAdtRuntimePaths {
  const configPath = opts?.configPath ?? LOCAL_CONFIG;
  if (!existsSync(configPath)) {
    return {};
  }
  return parseTomlConfig(configPath);
}

function parseTomlConfig(configPath: string): OpenAdtRuntimePaths {
  try {
    const parsed = Bun.TOML.parse(readFileSync(configPath, "utf8")) as {
      runtime?: { jco_native_dir?: string; sapcrypto?: string };
      jco_native_dir?: string;
      sapcrypto?: string;
    };
    const runtime = parsed.runtime;
    const jcoRaw = runtime?.jco_native_dir ?? parsed.jco_native_dir;
    const sapRaw = runtime?.sapcrypto ?? parsed.sapcrypto;
    return {
      jcoNativeDir: jcoRaw?.trim() || undefined,
      sapcrypto: sapRaw?.trim() || undefined,
    };
  } catch {
    return {};
  }
}

export function buildAdtLscSpawnRuntime(
  paths: OpenAdtRuntimePaths = loadOpenAdtRuntimePaths(),
): AdtLscSpawnRuntime {
  const rawEnv = ensureMinimalProcessEnv({ ...process.env });
  const view = new Env(rawEnv);
  const jvmArgs: string[] = [];
  const libraryPathEntries: string[] = [];

  if (paths.jcoNativeDir) {
    if (existsSync(paths.jcoNativeDir)) {
      view.prependPath(envVar(paths.jcoNativeDir));
    }
    libraryPathEntries.push(paths.jcoNativeDir);
  }
  if (paths.sapcrypto) {
    const sapDir = dirname(paths.sapcrypto);
    if (existsSync(paths.sapcrypto)) {
      view.prependPath(envVar(sapDir));
      rawEnv.SNC_LIB = paths.sapcrypto;
    }
    libraryPathEntries.push(sapDir);
    jvmArgs.push(`-Djco.middleware.snc_lib=${paths.sapcrypto}`);
  }

  if (libraryPathEntries.length > 0) {
    const sep = process.platform === "win32" ? ";" : ":";
    jvmArgs.push(`-Djava.library.path=${libraryPathEntries.join(sep)}`);
  }

  if (!rawEnv.SECUDIR?.trim()) {
    const secudirView = Env.fromProcess();
    const home = homedir();
    const appData = secudirView.string({ name: envVar("APPDATA") }) ?? "";
    const candidates: string[] = [];
    if (appData) {
      candidates.push(join(appData, "SAP", "Common"));
    }
    candidates.push(join(home, "AppData", "Roaming", "SAP", "Common"));
    candidates.push("C:\\Program Files\\SAP\\FrontEnd\\SecureLogin\\lib");
    candidates.push(join(home, ".openadt", "sec"));

    for (const candidate of candidates) {
      if (!existsSync(candidate)) {
        continue;
      }
      const normalized = candidate.replace(/\\/g, "/").toLowerCase();
      if (normalized.endsWith("/.openadt/sec")) {
        continue;
      }
      rawEnv.SECUDIR = candidate;
      break;
    }
  }

  return { env: rawEnv, jvmArgs };
}

/** Resolve Windows profile directory defaults from the given env view. */
export function resolveWindowsEnvDefaults(view: Env): WindowsEnvDefaults {
  const home = homedir();
  const localAppData =
    view.string({ name: envVar("LOCALAPPDATA") }) ??
    join(home, "AppData", "Local");
  const appData =
    view.string({ name: envVar("APPDATA") }) ??
    join(home, "AppData", "Roaming");
  const temp =
    view.string({ name: envVar("TEMP") }) ?? join(localAppData, "Temp");
  const systemRoot =
    view.string({ name: envVar("SystemRoot") }) ??
    view.string({ name: envVar("WINDIR") }) ??
    "C:\\Windows";
  return {
    [envVar("HOME")]: home,
    [envVar("USERPROFILE")]: home,
    [envVar("APPDATA")]: appData,
    [envVar("LOCALAPPDATA")]: localAppData,
    [envVar("TEMP")]: temp,
    [envVar("TMP")]: temp,
    [envVar("SystemRoot")]: systemRoot,
  } as WindowsEnvDefaults;
}

/** Cursor agent CLI passes a stripped env; fill Windows profile dirs for SECUDIR/JCo. */
export function ensureMinimalProcessEnv(
  env: NodeJS.ProcessEnv,
): NodeJS.ProcessEnv {
  if (process.platform !== "win32") {
    return env;
  }
  const view = new Env(env);
  const defaults = resolveWindowsEnvDefaults(view);
  for (const [name, value] of Object.entries(defaults)) {
    if (!view.getTrimmed(envVar(name))) {
      view.set({ name: envVar(name), value });
    }
  }
  return env;
}

export function isVsCodeAdtWorkspacePath(workspace: WorkspacePath): boolean {
  const normalized = workspace.replace(/\\/g, "/").toLowerCase();
  return (
    normalized.includes("workspacestorage") &&
    normalized.includes("sapse.adt-vscode/adtworkspace")
  );
}
