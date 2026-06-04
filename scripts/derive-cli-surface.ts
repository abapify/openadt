#!/usr/bin/env bun
// Derive the OpenADT CLI subcommand surface from specs/cli.md so /act agents
// can verify a reviewer claim ("does `openadt auth login` exist?") in one
// read instead of grepping apps/**.java.
//
// Surface = all `### openadt …` headings. Each entry preserves the full
// subcommand path (`openadt config bootstrap`, not just `openadt`).
//
// Usage:
//   bun scripts/derive-cli-surface.ts
//   bun scripts/derive-cli-surface.ts --check "openadt auth login"     # exit 0 + line
//   bun scripts/derive-cli-surface.ts --check "openadt auth login DEV" # exit 0 (prefix)
//   bun scripts/derive-cli-surface.ts --check "openadt adt logon"      # exit 1
//
// Saves ~600 tokens per lookup vs grepping the Java sources.
//
// Match rules for --check (most-specific first):
//   1. exact match on full command name
//   2. prefix-with-space match (e.g. "openadt auth login DEV" -> "openadt auth login")
// Fuzzy startsWith is deliberately NOT used: false positives are worse than
// false negatives for a verification tool.

import { readFileSync } from "node:fs";
import { resolve } from "node:path";

type Command = { name: string; spec_line: number };

const args = process.argv.slice(2);
let check: string | null = null;
let specPath = resolve("specs/cli.md");
for (let i = 0; i < args.length; i++) {
  if (args[i] === "--check" && args[i + 1]) {
    check = args[++i];
  } else if (!args[i].startsWith("--")) {
    specPath = resolve(args[i]);
  }
}

let text: string;
try {
  text = readFileSync(specPath, "utf8");
} catch (err) {
  const msg = err instanceof Error ? err.message : String(err);
  process.stderr.write(`error: cannot read ${specPath}: ${msg}\n`);
  process.exit(1);
}

const lines = text.split("\n");
const commands: Command[] = [];
const seen = new Set<string>();

for (let i = 0; i < lines.length; i++) {
  const m = lines[i].match(/^###\s+(openadt(?:\s+[^\[<\\\s]+)*)/);
  if (!m) continue;
  const name = m[1].replace(/\s+/g, " ").trim();
  if (seen.has(name)) continue;
  seen.add(name);
  commands.push({ name, spec_line: i + 1 });
}

if (check !== null) {
  const normalized = check.replace(/\s+/g, " ").trim();
  const userTokens = normalized.split(/\s+/);
  // Walk commands longest-first; a command matches when its token list is a
  // prefix of the user input. The single-token root `openadt` is only a match
  // for the bare input `openadt` — any extra token must be a known subcommand.
  const sorted = [...commands].sort((a, b) => b.name.length - a.name.length);
  let best: Command | null = null;
  for (const c of sorted) {
    const cmdTokens = c.name.split(/\s+/);
    if (cmdTokens.length > userTokens.length) continue;
    let ok = true;
    for (let i = 0; i < cmdTokens.length; i++) {
      if (cmdTokens[i] !== userTokens[i]) {
        ok = false;
        break;
      }
    }
    if (!ok) continue;
    if (cmdTokens.length === 1 && userTokens.length > 1) continue;
    best = c;
    break;
  }
  if (best) {
    process.stdout.write(`${best.name} @ ${specPath}:${best.spec_line}\n`);
    process.exit(0);
  }
  process.stdout.write(
    `NOT FOUND: ${check}\nknown:\n${commands.map((c) => `  ${c.name}`).join("\n")}\n`,
  );
  process.exit(1);
}

process.stdout.write(
  JSON.stringify({ source: specPath, commands }, null, 2) + "\n",
);
