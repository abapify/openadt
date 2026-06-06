import { existsSync } from "node:fs";

/** Branded env-var name — prevents accidental use of raw strings while remaining type-compatible. */
export type EnvVarName = string & { readonly __envVarName: unique symbol };

/** Create a typed env-var name constant. */
export const envVar = (name: string): EnvVarName => name as EnvVarName;

export type WindowsEnvDefaults = Record<EnvVarName, string>;

/** Branded workspace path — distinguishes filesystem paths from env-var names at the type level. */
export type WorkspacePath = string & {
  readonly __workspacePath: unique symbol;
};

export type GetStringOpts = {
  name: EnvVarName;
  default?: string;
  required?: boolean;
};

export type GetIntOpts = {
  name: EnvVarName;
  min?: number;
  max?: number;
};

export type GetPathOpts = {
  name: EnvVarName;
  mustExist?: boolean;
};

export type SetEnvOpts = {
  name: EnvVarName;
  value: string;
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

  getTrimmed(name: EnvVarName): string | undefined {
    const key = this.lookup.get(name.toUpperCase());
    const value = key === undefined ? undefined : this.env[key];
    const trimmed = typeof value === "string" ? value.trim() : undefined;
    return trimmed || undefined;
  }

  set(opts: SetEnvOpts): void {
    const key = this.lookup.get(opts.name.toUpperCase()) ?? opts.name;
    this.env[key] = opts.value;
    this.lookup.set(key.toUpperCase(), key);
  }

  /** Read a string env var. `default` applies when unset or blank; `required` throws otherwise. */
  string(opts: GetStringOpts): string | undefined {
    const raw = this.getTrimmed(opts.name);
    if (raw) {
      return raw;
    }
    if (opts.default !== undefined) {
      return opts.default;
    }
    if (opts.required) {
      throw new Error(`Missing required env var ${opts.name}`);
    }
    return undefined;
  }

  /** Parse an env var as an integer within `[min, max]`. Returns undefined when unset/blank. */
  integer(opts: GetIntOpts): number | undefined {
    const raw = this.getTrimmed(opts.name);
    if (!raw) {
      return undefined;
    }
    return validateInteger(opts.name, raw, opts.min, opts.max);
  }

  /** Read a filesystem path env var; reject when `mustExist` and the path is absent. */
  path(opts: GetPathOpts): string | undefined {
    const raw = this.getTrimmed(opts.name);
    if (!raw) {
      return undefined;
    }
    if (opts.mustExist && !existsSync(raw)) {
      return undefined;
    }
    return raw;
  }

  prependPath(dir: EnvVarName): void {
    const sep = pathSeparator();
    const pathKey = this.findKey("PATH");
    const current = this.env[pathKey] ?? "";
    if (pathContains(current, dir, sep)) {
      return;
    }
    this.env[pathKey] = current ? `${dir}${sep}${current}` : dir;
  }

  private findKey(upperName: string): string {
    return (
      Object.keys(this.env).find((k) => k.toUpperCase() === upperName) ??
      upperName
    );
  }
}

function pathSeparator(): string {
  return process.platform === "win32" ? ";" : ":";
}

function pathContains(current: string, dir: string, sep: string): boolean {
  return current
    .split(sep)
    .some((entry) => entry.toLowerCase() === dir.toLowerCase());
}

function validateInteger(
  name: EnvVarName,
  raw: string,
  min?: number,
  max?: number,
): number {
  const value = Number(raw);
  if (!Number.isInteger(value)) {
    throw new Error(`Env ${name}=${raw} is not an integer`);
  }
  assertRange(name, value, min, max);
  return value;
}

function assertRange(
  name: EnvVarName,
  value: number,
  min?: number,
  max?: number,
): void {
  if (min !== undefined && value < min) {
    throw new Error(`Env ${name}=${value} below min ${min}`);
  }
  if (max !== undefined && value > max) {
    throw new Error(`Env ${name}=${value} above max ${max}`);
  }
}
