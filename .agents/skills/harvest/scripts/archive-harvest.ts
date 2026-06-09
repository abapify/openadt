#!/usr/bin/env bun
/**
 * Archive harvest files whose rows have all been triaged (status in
 * `ledger.jsonl` is `done` | `wontfix` | `duplicate`).
 *
 * Used by `/backlog harvest` after the rows are written into
 * `.agents/backlog/*.md` — once a harvest file has no live rows left, it is
 * moved under `.agents/review-debt/archive/harvests/` so the next /harvest
 * starts from a clean ledger.
 *
 * Usage:
 *   bun .agents/skills/harvest/scripts/archive-harvest.ts
 *   bun .agents/skills/harvest/scripts/archive-harvest.ts --dry-run
 *   bun run harvest:archive
 */
import {
  existsSync,
  mkdirSync,
  readdirSync,
  readFileSync,
  renameSync,
  writeFileSync,
} from "node:fs";
import { dirname, join } from "node:path";
import {
  listHarvestPaths,
  readDebtRecords,
  readLedgerOverlays,
  type DebtRecord,
  type DebtStatus,
  type LedgerOverlay,
} from "./review-debt-lib.ts";

const ARCHIVE_DIR_NAME = "archive";

function archiveDir(): string {
  return join(dirname(listHarvestPaths()[0] ?? ""), ARCHIVE_DIR_NAME);
}

interface LiveThreadIds {
  live: Set<string>;
  totals: { harvested: number; open: number; archived: number };
}

const TERMINAL_STATUSES = new Set<DebtStatus>(["done", "wontfix", "duplicate"]);

function isTerminal(status: DebtStatus): boolean {
  return TERMINAL_STATUSES.has(status);
}

function isOpen(overlays: Map<string, LedgerOverlay>, row: DebtRecord): boolean {
  return (overlays.get(row.thread_id)?.status ?? row.status) === "open";
}

function computeLiveThreadIds(): LiveThreadIds {
  const all = readDebtRecords();
  const overlays = readLedgerOverlays();
  const live = new Set<string>();
  for (const row of all) {
    const status = overlays.get(row.thread_id)?.status ?? row.status;
    if (isTerminal(status)) {
      continue;
    }
    live.add(row.thread_id);
  }
  return {
    live,
    totals: {
      harvested: all.length,
      open: all.filter((r) => isOpen(overlays, r)).length,
      archived: 0,
    },
  };
}

function archivedCount(): number {
  const dir = archiveDir();
  if (!existsSync(dir)) {
    return 0;
  }
  return readdirSync(dir).filter((n) => n.endsWith(".jsonl")).length;
}

interface ArchiveFileResult {
  path: string;
  archivedAt: string;
  removedRows: number;
  liveRows: number;
}

function archiveFile(opts: {
  path: string;
  liveThreadIds: Set<string>;
  dryRun: boolean;
}): ArchiveFileResult | null {
  if (!existsSync(opts.path)) {
    return null;
  }
  const raw = readFileSync(opts.path, "utf8");
  const lines = raw
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
  let liveRows = 0;
  for (const line of lines) {
    try {
      const row = JSON.parse(line) as { thread_id: string };
      if (opts.liveThreadIds.has(row.thread_id)) {
        liveRows += 1;
      }
    } catch {
      liveRows += 1;
    }
  }
  if (liveRows > 0) {
    return null;
  }
  const archivedAt = new Date().toISOString();
  if (opts.dryRun) {
    return { path: opts.path, archivedAt, removedRows: lines.length, liveRows: 0 };
  }
  const dest = join(archiveDir(), opts.path.split("/").pop()!);
  renameSync(opts.path, dest);
  return { path: dest, archivedAt, removedRows: lines.length, liveRows: 0 };
}

function writeArchiveMarker(opts: { dest: string; archivedAt: string; removedRows: number }): void {
  const marker = opts.dest.replace(/\.jsonl$/, ".archived.json");
  writeFileSync(
    marker,
    `${JSON.stringify({ archived_at: opts.archivedAt, rows: opts.removedRows }, null, 2)}\n`,
    "utf8",
  );
}

function parseArgs(argv: string[]): { dryRun: boolean } {
  return { dryRun: argv.includes("--dry-run") };
}

export function main(): void {
  const args = parseArgs(process.argv.slice(2));
  const { live, totals } = computeLiveThreadIds();
  const paths = listHarvestPaths();
  if (paths.length === 0) {
    console.error("archive: no harvest files found");
    return;
  }
  if (!args.dryRun) {
    mkdirSync(archiveDir(), { recursive: true });
  }
  let archived = 0;
  let removedRows = 0;
  for (const path of paths) {
    const result = archiveFile({ path, liveThreadIds: live, dryRun: args.dryRun });
    if (!result) {
      continue;
    }
    archived += 1;
    removedRows += result.removedRows;
    if (!args.dryRun) {
      writeArchiveMarker({
        dest: result.path,
        archivedAt: result.archivedAt,
        removedRows: result.removedRows,
      });
    }
  }
  totals.archived = archivedCount();
  console.error(
    `archive: ${archived} file(s) ${args.dryRun ? "would archive" : "archived"} ` +
      `(${removedRows} row(s)); ledger now ${totals.open} open, ${totals.harvested} harvested, ${totals.archived} archived`,
  );
}

if (import.meta.main) {
  main();
}
