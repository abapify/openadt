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
 *   bun run harvest:test                                                  run
 *                                                                            /harvest
 *                                                                            unit tests
 */
import { spawnSync } from "node:child_process";
import { resolveGhRepo } from "./review-debt-gh.ts";

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
  const repo = resolveGhRepo();
  if (!repo) {
    return rest;
  }
  return [repo.owner, repo.repo, ...rest];
}

function usage(): never {
  console.error(`Usage:
  bun run harvest:<cmd> -- [args…]

Commands:
  pr        Harvest one merged PR (positional: PR_NUMBER, --merged-sha, --run-id)
  batch     Batch harvest merged PRs (--pr-ids, --merged-since, --last, …)
  resolve   Resolve PR + merge SHA from a GH Actions event (writes outputs)
  archive   Move fully-triaged harvests/*.jsonl into archive/ (used by /backlog harvest)
  test      Run /harvest unit tests

Examples:
  bun run harvest:pr -- 72 --dry-run
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

function runCommand(cmd: string, rest: string[]): number {
  if (cmd === "test") {
    return runTests();
  }
  const subcommand = cmd as Subcommand;
  const args = REPO_SCOPED.has(subcommand as RepoScoped)
    ? withOwnerRepo(subcommand as RepoScoped, rest)
    : rest;
  return runBun(subcommandScript(subcommand), args);
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
