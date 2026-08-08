# SkillSpector CI gate

Security scan of agent-skill assets committed to this repository using
[NVIDIA SkillSpector](https://github.com/nvidia/skillspector) — a scanner for
AI agent skills (Claude Code, Codex CLI, Gemini CLI style `SKILL.md`
packages). OpenADT ships a `.agents/skills/` tree that is loaded with
implicit trust by development agents; the gate exists to detect
prompt-injection, data-exfiltration, supply-chain, and dangerous-code patterns
in those skills.

## Scope

| Aspect        | Value                                                                                       |
| ------------- | ------------------------------------------------------------------------------------------- |
| Scan target   | `.agents/skills/` (and any first-party skill at the repo root that follows the `SKILL.md` + assets layout) |
| Tool          | `skillspector` CLI `v2.8.1` (immutable commit `0a1546b03827b08035eb011d525770c7bb29d6c2`, see [SkillSpector pin](packaging.md#skillspector-pin)) |
| Analysis mode | `--no-llm` (static only; no API keys, deterministic, fast)                                 |
| Baseline      | `.skillspector-baseline.yaml` — accepted false positives with rationale                    |
| SARIF filter  | `scripts/strip-skillspector-suppressions.py` — removes suppressed results before upload    |
| Output        | SARIF, uploaded to GitHub Code Scanning under category `skillspector`                       |
| Failure mode  | **Advisory** — exit code 1 (findings) does not fail the workflow. Exit code ≥ 2 (tool error) does. Same convention as the OpenGrep job in [ci.yml](../.github/workflows/ci.yml). |
| Triggers      | `push` to `main`, every `pull_request`                                                      |
| Runner        | `ubuntu-latest`                                                                             |

## Rationale

- The `opengrep` job covers general SAST on product code. The skillspector job
  covers the *agent-skill* attack surface specifically (prompt injection,
  exfiltration patterns, AST-level `exec`/`eval`/`subprocess`, MCP tool
  poisoning) which opengrep's generic ruleset does not.
- Research cited upstream: ~26 % of third-party agent skills contain a
  vulnerability; ~5 % show likely malicious intent. Several of OpenADT's
  skills (e.g. `act`, `harvest`, `memory-bank`) are imported from third
  parties, so scanning on every PR catches drift.

## What it does NOT do

- It does not call any LLM provider. No `OPENAI_API_KEY` / `ANTHROPIC_API_KEY`
  / `NVIDIA_INFERENCE_KEY` is wired into CI.
- It does not scan the Java or TypeScript product code. Use the opengrep job
  for that.
- It does not scan `.agents/memory/`, `.agents/backlog/`, or
  `.agents/review-debt/` — those are agent-authored working memory, not
  installable skills. Scanning them produces noise without security signal.

## Promoting to blocking

The first run will almost certainly fire findings on existing skills that
legitimately reference `subprocess`, `os.environ`, dynamic imports, or
network endpoints (e.g. `openadt-local-sap-runtime` documents JCo
invocation; `openadt-product` references subprocess flows). The promotion
from advisory to blocking is a separate, explicit decision:

1. Land the advisory gate (this spec + workflow).
2. Triage the baseline findings — each becomes either a fix or a documented
   suppression in `.skillspector-baseline.yaml` with a rationale in this spec.
3. Flip the failure policy to `exit 1 = fail` in a second PR.

### Why suppressed results are stripped before upload

SkillSpector's `--baseline` flag emits SARIF `suppressions` entries so the
audit trail is preserved. GitHub Code Scanning does **not** honor SARIF
`suppressions` natively, so the CI workflow runs
`scripts/strip-skillspector-suppressions.py` to remove baseline-suppressed
results before `github/codeql-action/upload-sarif`. This makes the Security
tab reflect only active findings.

## Baseline findings (advisory, 2026-08-07)

Live SARIF from SkillSpector `v2.8.1`. Triage outcomes are enforced by
`.skillspector-baseline.yaml` and listed here as the audit log.

| # | Rule | Severity | File:line | Triage | Rationale |
|---|------|----------|-----------|--------|-----------|
| 1  | AS3 | warning  | `.agents/skills/act/scripts/review-debt-cli.ts:5` | **Suppress** | Cross-reference to first-party `harvest/SKILL.md` is an intentional documentation link. |
| 2  | AS3 | warning  | `.agents/skills/backlog/SKILL.md:59` | **Suppress** | Cross-reference to first-party `harvest/SKILL.md` is an intentional documentation link. |
| 3  | AS3 | warning  | `.agents/skills/codacy/SKILL.md:203` | **Suppress** | Cross-reference to first-party `act/SKILL.md` is an intentional documentation link. |
| 4  | AS3 | warning  | `.agents/skills/e2e/scripts/framework/dispatch.ts:205` | **Suppress** | Cross-reference to first-party `e2e/SKILL.md` is an intentional documentation link. |
| 5  | RP1 | warning  | `.agents/skills/codacy/SKILL.md:66` | **Suppress** | False positive. `docker run` references a locally-built `codacy-java` image for local PMD reproduction, not an unpinned MCP server install. |
| 6  | E1  | warning  | `.agents/skills/codacy/SKILL.md:67` | **Suppress** | False positive. The documentation example uses `curl` to download a pinned PMD release archive into a local tmp directory; it does not transmit data to an external endpoint. |
| 7  | PE3 | error    | `.agents/skills/codescene/SKILL.md:17` | **Suppress** | False positive. The skill *mentions* the name `CS_ACCESS_TOKEN` to document rotation/troubleshooting; the actual secret lives in the `abapify` org's GitHub Actions secrets and is never written to disk by OpenADT. Any future PE3 finding on a file that **reads** a token value would be triaged separately. |
| 8  | TM3 | warning  | `.agents/skills/harvest/SKILL.md:22`  | **Suppress** | False positive. Documents the `ignore_authors` / `nit_authors` config keys — feature is the *purpose* of the harvest config. |
| 9  | TM3 | warning  | `.agents/skills/harvest/scripts/review-debt-lib.ts:72` | **Suppress** | False positive. `DebtConfig.ignore_authors: string[]` is the typed deny-list field — required for the feature. |
| 10 | TM3 | warning  | `.agents/skills/harvest/scripts/review-debt-lib.ts:104` | **Suppress** | False positive. Empty-array fallback when `config.json` is missing. Hardening this to refuse-to-run would break `/harvest` on first use. |
| 11 | TM3 | warning  | `.agents/skills/harvest/scripts/review-debt-lib.ts:111` | **Suppress** | False positive. `parsed.ignore_authors ?? []` is the missing-field default — same as #10. |
| 12 | TM3 | warning  | `.agents/skills/harvest/scripts/review-debt-lib.ts:111` | **Suppress** | Same line as #11, second instance of the same deny-list field. |
| 13 | TM3 | warning  | `.agents/skills/harvest/scripts/review-debt-lib.ts:337` | **Suppress** | False positive. The `ignore_authors.some(a => a.toLowerCase() === login)` predicate is the **allow-or-deny** check itself. Removing it inverts the feature. |

v2.8.1's context filtering removed the following earlier false positives without
needing a baseline entry:

- **TM1** (`codacy/SKILL.md:66`) — `docker run --rm` is now downweighted below
the `--no-llm` confidence threshold because it is recognized as a safe container
command.
- **RA2** (`memory-bank/SKILL.md:39`) — the documented memory path is now
recognized as a code example in a non-executable markdown file and filtered.
- **TM3** (`harvest/SKILL.md:113`) — the JSON config example is now recognized
as a code example and filtered.

### Why not rename `ignore_authors` to silence TM3?

The `ignore_authors` field is consumed by callers and by `config.json` on
disk. Renaming it would be a breaking change to the harvest contract (the
field is part of the public `/harvest` config schema, documented in
`harvest/SKILL.md`). The static-analysis rule is firing on a feature
that exists by design, not on a defect.

### How to verify suppression holds

1. Run SkillSpector locally with the baseline (fail-closed):
   ```bash
   set -euo pipefail
   rm -f skillspector-raw.sarif skillspector.sarif
   scan_status=0
   skillspector scan .agents/skills/ \
     --no-llm \
     --baseline .skillspector-baseline.yaml \
     --format sarif \
     --output skillspector-raw.sarif || scan_status=$?
   if [ "$scan_status" -gt 1 ]; then
     exit "$scan_status"
   fi
   python scripts/strip-skillspector-suppressions.py skillspector-raw.sarif skillspector.sarif
   jq -e '(.runs | type == "array") and ([.runs[] | (.results // [])[]] | length == 0)' skillspector.sarif
   if [ "$scan_status" -eq 1 ]; then
     exit 1
   fi
   ```
   This removes stale SARIF files, preserves SkillSpector's `0`/`1`/`>1` exit
   semantics, and asserts the stripped `skillspector.sarif` contains zero `results`.
2. Any new `error` or `warning` finding not covered by `.skillspector-baseline.yaml`
   must be fixed in the same PR or added to this table (and the YAML) with a
   rationale — never silently bypassed.

## Maintenance

- Bump the pinned SkillSpector release tag **and** immutable commit SHA when
  upstream ships a new minor version with relevant pattern additions.
- Re-baseline the SARIF category if the tool's rule IDs change in a way that
  suppresses historical findings.
- SkillSpector is Apache-2.0; it is **not** vendored in the repo. The
  workflow downloads the release binary on each run, mirroring how OpenGrep
  is pinned in `ci.yml`.

## Related contracts

The SkillSpector rule set overlaps with the [verify-agent-memory](../scripts/verify-agent-memory.ts)
linter and the e2e dependency contract:

| Contract | Where | Why it lives there |
| -------- | ----- | ------------------ |
| Memory redaction rules (no real SIDs, no `--destination` values, no `/e2e <code> <sid>`) | [agent-memory-landscape-redaction.md](../.agents/memory/mental-models/agent-memory-landscape-redaction.md) + [`scripts/verify-agent-memory.ts`](../scripts/verify-agent-memory.ts) | Detected at PR time by `bun scripts/verify-fixtures-only.ts`. Per-line: one diagnostic per line, never one-per-match. |
| e2e runtime dependency pin policy | [`.agents/skills/e2e/SPEC.md` § Runtime dependencies](../.agents/skills/e2e/SPEC.md#runtime-dependencies-e2e-agent) | SkillSpector SC1/SC4 fires on caret ranges. Bumping any pinned dep requires a spec amendment in this table. |
