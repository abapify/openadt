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
//   bun scripts/derive-cli-surface.ts --check "openadt auth login"   # exit 0 + line
//   bun scripts/derive-cli-surface.ts --check "openadt adt logon"    # exit 1
//
// Saves ~600 tokens per lookup vs grepping the Java sources.

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

const text = readFileSync(specPath, "utf8");
const lines = text.split("\n");
const commands: Command[] = [];
const seen = new Set<string>();

for (let i = 0; i < lines.length; i++) {
  const m = lines[i].match(/^###\s+(openadt(?:\s+[^\[<\\\s]+)*)/);
  if (!m) continue;
  // Strip angle/bracket placeholders, keep just the command path.
  const name = m[1].replace(/\s+/g, " ").trim();
  if (seen.has(name)) continue;
  seen.add(name);
  commands.push({ name, spec_line: i + 1 });
}

if (check !== null) {
  const hit = commands.find((c) => c.name === check);
  if (hit) {
    process.stdout.write(`${hit.name} @ specs/cli.md:${hit.spec_line}\n`);
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
