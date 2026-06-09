import { describe, expect, test } from "bun:test";
import {
  existsSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { main as runArchive } from "./archive-harvest.ts";
import type { DebtRecord } from "./review-debt-lib.ts";

interface ArchiveEnv {
  tmp: string;
  debtDir: string;
}

const activeEnvs: ArchiveEnv[] = [];

function setupArchiveEnv(prefix: string): ArchiveEnv {
  const tmp = mkdtempSync(join(tmpdir(), `archive-harvest-${prefix}-`));
  const debtDir = join(tmp, "review-debt");
  const harvests = join(debtDir, "harvests");
  mkdirSync(harvests, { recursive: true });
  process.env.OPENADT_DEBT_DIR = debtDir;
  process.env.OPENADT_LEDGER_FILE = join(debtDir, "ledger.jsonl");
  process.env.OPENADT_DEBT_SUMMARY = join(debtDir, "debt-summary.json");
  process.env.OPENADT_DEBT_FILE = join(debtDir, "debt.jsonl");
  const env: ArchiveEnv = { tmp, debtDir };
  activeEnvs.push(env);
  return env;
}

function cleanupAll(): void {
  for (const env of activeEnvs) {
    rmSync(env.tmp, { recursive: true, force: true });
  }
  activeEnvs.length = 0;
  delete process.env.OPENADT_DEBT_DIR;
  delete process.env.OPENADT_LEDGER_FILE;
  delete process.env.OPENADT_DEBT_SUMMARY;
  delete process.env.OPENADT_DEBT_FILE;
}

function makeRecord(overrides: Partial<DebtRecord>): DebtRecord {
  return {
    thread_id: "PRRT_x",
    thread_url: "https://example.com#x",
    status: "open",
    priority: "nit",
    needs: "code_change",
    source_pr: 1,
    source_pr_url: "https://example.com/p/1",
    source_pr_title: "t",
    merged_at: "2026-01-01T00:00:00Z",
    merged_sha: "abc",
    path: "a/b.ts",
    line: 1,
    author: "x",
    body: "x",
    body_preview: "x",
    fingerprint: "sha256:x",
    area: "a",
    harvested_at: "2026-01-01T00:00:00Z",
    harvest_run_id: "r",
    times_seen: 1,
    fix_pr: null,
    fixed_at: null,
    notes: null,
    ...overrides,
  };
}

function invoke(args: string[]): { stderr: string } {
  const prevArgv = process.argv;
  const stderrLines: string[] = [];
  const original = console.error;
  console.error = (...msgs: unknown[]) => {
    stderrLines.push(msgs.map((m) => (typeof m === "string" ? m : JSON.stringify(m))).join(" "));
  };
  process.argv = ["bun", "archive-harvest.ts", ...args];
  try {
    runArchive();
  } catch (err) {
    stderrLines.push(`THROWN: ${err instanceof Error ? err.stack ?? err.message : String(err)}`);
  } finally {
    console.error = original;
    process.argv = prevArgv;
  }
  return { stderr: stderrLines.join("\n") };
}

function writeHarvest(debtDir: string, rows: DebtRecord[]): string {
  const harvests = join(debtDir, "harvests");
  const file = join(harvests, "2026-01-01T000000Z-pr-1-run-test.jsonl");
  writeFileSync(file, rows.map((r) => JSON.stringify(r)).join("\n") + "\n", "utf8");
  return file;
}

function writeLedger(debtDir: string, overlays: object[]): void {
  writeFileSync(
    join(debtDir, "ledger.jsonl"),
    overlays.map((o) => JSON.stringify(o)).join("\n") + "\n",
    "utf8",
  );
}

const HARVEST_FILE = "2026-01-01T000000Z-pr-1-run-test.jsonl";

function checkAllDone(debtDir: string): {
  archiveExists: boolean;
  origStillThere: boolean;
  markerExists: boolean;
} {
  return {
    archiveExists: existsSync(join(debtDir, "archive", "harvests", HARVEST_FILE)),
    origStillThere: existsSync(join(debtDir, "harvests", HARVEST_FILE)),
    markerExists: existsSync(
      join(debtDir, "archive", "harvests", HARVEST_FILE.replace(/\.jsonl$/, ".archived.json")),
    ),
  };
}

function checkMixed(debtDir: string): { stillThere: boolean; archivedAway: boolean } {
  return {
    stillThere: existsSync(join(debtDir, "harvests", HARVEST_FILE)),
    archivedAway: existsSync(join(debtDir, "archive", "harvests", HARVEST_FILE)),
  };
}

describe("archive-harvest", () => {
  test("archives a file when all rows are triaged in ledger", () => {
    const env = setupArchiveEnv("all-done");
    writeHarvest(env.debtDir, [makeRecord({ thread_id: "PRRT_done" })]);
    writeLedger(env.debtDir, [
      { thread_id: "PRRT_done", status: "done", fix_pr: 9, fixed_at: "2026-02-01T00:00:00Z", notes: null },
    ]);

    const { stderr } = invoke([]);
    const checks = checkAllDone(env.debtDir);
    cleanupAll();
    expect(stderr).toMatch(/1 file\(s\) archived/);
    expect(checks.archiveExists).toBe(true);
    expect(checks.origStillThere).toBe(false);
    expect(checks.markerExists).toBe(true);
  });

  test("keeps the file when at least one row is still open", () => {
    const env = setupArchiveEnv("mixed");
    writeHarvest(env.debtDir, [
      makeRecord({ thread_id: "PRRT_done" }),
      makeRecord({ thread_id: "PRRT_open" }),
    ]);
    writeLedger(env.debtDir, [
      { thread_id: "PRRT_done", status: "done", fix_pr: 9, fixed_at: "2026-02-01T00:00:00Z", notes: null },
    ]);

    const { stderr } = invoke([]);
    const checks = checkMixed(env.debtDir);
    cleanupAll();
    expect(stderr).toMatch(/0 file\(s\) archived/);
    expect(checks.stillThere).toBe(true);
    expect(checks.archivedAway).toBe(false);
  });

  test("--dry-run does not move files", () => {
    const env = setupArchiveEnv("dry-run");
    const file = writeHarvest(env.debtDir, [makeRecord({ thread_id: "PRRT_done" })]);
    writeLedger(env.debtDir, [
      { thread_id: "PRRT_done", status: "wontfix", fix_pr: null, fixed_at: null, notes: "out of scope" },
    ]);

    const { stderr } = invoke(["--dry-run"]);
    const stillThere = existsSync(file);
    const archiveDirExists = existsSync(join(env.debtDir, "archive", "harvests"));
    cleanupAll();
    expect(stillThere).toBe(true);
    expect(archiveDirExists).toBe(false);
    expect(stderr).toMatch(/1 file\(s\) would archive/);
  });

  test("writes archived marker with row count", () => {
    const env = setupArchiveEnv("marker");
    writeHarvest(env.debtDir, [
      makeRecord({ thread_id: "PRRT_a" }),
      makeRecord({ thread_id: "PRRT_b" }),
    ]);
    writeLedger(env.debtDir, [
      { thread_id: "PRRT_a", status: "done", fix_pr: 9, fixed_at: "2026-02-01T00:00:00Z", notes: null },
      { thread_id: "PRRT_b", status: "duplicate", fix_pr: null, fixed_at: null, notes: null },
    ]);

    invoke([]);
    const marker = readFileSync(
      join(env.debtDir, "archive", "harvests", HARVEST_FILE.replace(/\.jsonl$/, ".archived.json")),
      "utf8",
    );
    cleanupAll();
    const parsed = JSON.parse(marker) as { rows: number };
    expect(parsed.rows).toBe(2);
  });
});
