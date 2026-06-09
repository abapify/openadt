#!/usr/bin/env bun
/**
 * Entry point for /harvest scripts (`bun run harvest:*`).
 *
 * /harvest collects review threads ("the harvest"). What happens with the
 * harvest downstream — `/backlog` triage, `/act` batch fix, source-PR resolve —
 * is owned by other skills. /harvest only writes
 * `.agents/review-debt/harvests/*.jsonl` and `.agents/review-debt/debt-summary.json`.
 *
 * Commands:
 *   bun run harvest:pr      -- 72 [--dry-run]                       single PR
 *   bun run harvest:batch   -- --pr-ids 72,67 [--dry-run]           filtered batch
 *   bun run harvest:resolve                                                  write
 *                                                                            should_harvest
 *                                                                            outputs
 *                                                                            from a GH
 *                                                                            event
 *   bun run harvest:archive                                                  move
 *                                                                            fully-triaged
 *                                                                            harvests
 *                                                                            into
 *                                                                            archive/
 *   bun run harvest:test                                                  run
 *                                                                            /harvest
 *                                                                            unit tests
 *
 * `pr` and `batch` accept `OWNER REPO` as the first two positionals. When
 * missing, the wrapper resolves them from `GITHUB_REPOSITORY` or
 * `gh repo view` (clone with `gh auth`).
 */
import { spawnSync } from "node:child_process";

const SCRIPT_DIR = import.meta.dir;

const SUBCOMMANDS = {
  pr: "harvest-threads.ts",
  batch: "harvest-debt-batch.ts",
  resolve: "resolve-harvest-target.ts",
  archive: "archive-harvest.ts",
} as const;

type Subcommand = keyof typeof SUBCOMMANDS;
type RepoScoped = "pr" | "batch";

const REPO_SCOPED = new Set<RepoScoped>(["pr", "batch"]);

function usage(): never {
  console.error(`Usage:
  bun run harvest:<cmd> -- [args…]

Commands:
  pr        Harvest one merged PR (positional: [OWNER REPO] PR_NUMBER, --merged-sha, --run-id)
  batch     Batch harvest merged PRs ([OWNER REPO] --pr-ids, --merged-since, --last, …)
  resolve   Resolve PR + merge SHA from a GH Actions event (writes outputs)
  archive   Move fully-triaged harvests/*.jsonl into archive/ (used by /backlog harvest)
  test      Run /harvest unit tests

OWNER REPO defaults from \`GITHUB_REPOSITORY\` or \`gh repo view\` when not given.

Examples:
  bun run harvest:pr -- 72 --dry-run
  bun run harvest:pr -- abapify openadt 72 --dry-run
  bun run harvest:batch -- --pr-ids 72,67 --dry-run
  bun run harvest:batch -- --merged-since 2026-06-09 --last 5
  bun run harvest:archive -- --dry-run`);
  process.exit(1);
}

function runBun(script: string, args: string[]): number {
  const result = spawnSync("bun", [`${SCRIPT_DIR}/${script}`, ...args], {
    stdio: "inherit",
  });
  if (result.error) {
    throw result.error;
  }
  return result.status ?? 1;
}

function runTests(): number {
  const tests = [
    "review-debt-lib.test.ts",
    "resolve-harvest-prs.test.ts",
    "resolve-harvest-target.test.ts",
    "archive-harvest.test.ts",
  ];
  const paths = tests.map((t) => `${SCRIPT_DIR}/${t}`);
  const result = spawnSync("bun", ["test", ...paths], { stdio: "inherit" });
  if (result.error) {
    throw result.error;
  }
  return result.status ?? 1;
}

function wantsUsage(cmd: string | undefined): boolean {
  if (!cmd) {
    return true;
  }
  return cmd === "--help" || cmd === "-h";
}

function subcommandScript(cmd: string): string {
  const script = SUBCOMMANDS[cmd as Subcommand];
  if (!script) {
    console.error(`Unknown command: ${cmd}`);
    usage();
  }
  return script;
}

function resolveGhRepo(): { owner: string; repo: string } {
  const slug = process.env.GITHUB_REPOSITORY;
  if (slug) {
    const [owner, repo] = slug.split("/");
    if (owner && repo) {
      return { owner, repo };
    }
  }
  const proc = spawnSync(
    "gh",
    ["repo", "view", "--json", "owner,name", "-q", '.owner.login + " " + .name'],
    { encoding: "utf8" },
  );
  if (proc.status !== 0) {
    throw new Error(
      "Could not resolve GitHub owner/repo. Run from a gh-authenticated clone, set GITHUB_REPOSITORY, or pass OWNER REPO explicitly.",
    );
  }
  const [owner, repo] = (proc.stdout ?? "").trim().split(/\s+/);
  if (!owner || !repo) {
    throw new Error(
      "Could not resolve GitHub owner/repo. Run from a gh-authenticated clone, set GITHUB_REPOSITORY, or pass OWNER REPO explicitly.",
    );
  }
  return { owner, repo };
}

function needsOwnerRepoPrefix(rest: string[]): boolean {
  const first = rest[0];
  if (!first || first.startsWith("--")) {
    return true;
  }
  const second = rest[1];
  if (!second || second.startsWith("--")) {
    return true;
  }
  return false;
}

function withOwnerRepo(cmd: RepoScoped, rest: string[]): string[] {
  if (!needsOwnerRepoPrefix(rest)) {
    return rest;
  }
  const { owner, repo } = resolveGhRepo();
  return [owner, repo, ...rest];
}

function runCommand(cmd: string, rest: string[]): number {
  if (cmd === "test") {
    return runTests();
  }
  const script = subcommandScript(cmd);
  const args = REPO_SCOPED.has(cmd as RepoScoped)
    ? withOwnerRepo(cmd as RepoScoped, rest)
    : rest;
  return runBun(script, args);
}

function main(): void {
  const [cmd, ...rest] = process.argv.slice(2);
  if (wantsUsage(cmd)) {
    usage();
  }
  process.exit(runCommand(cmd!, rest));
}

if (import.meta.main) {
  main();
}
