/**
 * Dev CLI entry for `bun run openadt`. MCP uses the Bun launcher only — skip Nx/Maven compile.
 */
import { spawnSync } from "node:child_process";
import { join } from "node:path";

const repoRoot = join(import.meta.dir, "..");

function firstSubcommand(args: string[]): string | undefined {
  for (let i = 0; i < args.length; i++) {
    const arg = args[i]!;
    if (arg === "--") {
      return args[i + 1];
    }
    if (arg.startsWith("--")) {
      continue;
    }
    if (arg.startsWith("-")) {
      continue;
    }
    return arg;
  }
  return undefined;
}

const args = process.argv.slice(2);

if (firstSubcommand(args) === "mcp") {
  const result = spawnSync(
    "bun",
    [join(repoRoot, "scripts", "nx-openadt.ts"), ...args],
    {
      stdio: "inherit",
      cwd: repoRoot,
      env: { ...process.env, OPENADT_REPO: repoRoot },
    },
  );
  process.exit(result.status ?? 1);
}

const result = spawnSync(
  "nx",
  ["run", "openadt-cli:run", "--output-style=stream", "--", ...args],
  { stdio: "inherit", cwd: repoRoot, shell: true },
);
process.exit(result.status ?? 1);
