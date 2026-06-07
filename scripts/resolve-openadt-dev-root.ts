import { existsSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";

function isRepoRoot(path: string): boolean {
  return existsSync(join(path, "apps", "openadt-cli"));
}

/** Resolve openadt clone root for dev-openadt / openadt-dev.exe (works when compiled). */
export function resolveOpenadtDevRoot(): string {
  for (const key of ["OPENADT_DEV_ROOT", "OPENADT_REPO"] as const) {
    const raw = process.env[key]?.trim();
    if (raw && isRepoRoot(raw)) {
      return raw;
    }
  }

  const fromSource = join(import.meta.dir, "..");
  if (isRepoRoot(fromSource)) {
    return fromSource;
  }

  const hintPath = join(dirname(process.execPath), "openadt-dev.root");
  if (existsSync(hintPath)) {
    const raw = readFileSync(hintPath, "utf8").trim();
    if (raw && isRepoRoot(raw)) {
      process.env.OPENADT_DEV_ROOT = raw;
      return raw;
    }
  }

  console.error(
    "openadt-dev: cannot find the openadt clone.\n" +
      "  Set OPENADT_DEV_ROOT to your clone path, or re-run:\n" +
      "  .\\scripts\\install-openadt-dev.ps1",
  );
  process.exit(1);
}
