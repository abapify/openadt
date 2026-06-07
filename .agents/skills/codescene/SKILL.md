---
name: codescene
description: >-
  CodeScene setup and usage — CI delta analysis, local CLI, CS_ACCESS_TOKEN,
  Docker image, troubleshooting 403s and secret visibility.
---

# CodeScene

CodeScene integration for OpenADT. Two layers:

1. **CodeScene GitHub App** — runs "Delta Analysis" and "Code Health Review (main)" on PRs automatically. This is the primary quality gate.
2. **`cs` CLI via Docker** — manual delta analysis triggered by `workflow_dispatch` or locally. Used for deeper investigation or when the App output needs reproducing.

## CS_ACCESS_TOKEN

The `CS_ACCESS_TOKEN` is an **org-level secret** under the `abapify` GitHub organization. It is a Personal Access Token (PAT) from <https://codescene.io/users/me/pat>.

When the token is valid and exposed, it also grants access to the **CodeScene Cloud API** and the **`cs` CLI** — the same token works for both.

### Secret availability problem

Branch protection rules can prevent `CS_ACCESS_TOKEN` from being available to automated bots / dependabot PRs. The secret is set at the org level but GitHub silently omits it when the triggering actor (bot) is not trusted by branch protection. This is why the delta workflow is **manual-only** (`workflow_dispatch`) — a human trigger always has permission to use the secret.

If you see `CS_ACCESS_TOKEN org secret missing or not granted to this repo`:
- Verify the secret exists: `gh secret list` (requires admin).
- The bot/actor may be blocked by branch protection — trigger manually or adjust protection rules.

## CI setup (GitHub Actions)

### Delta workflow (manual trigger)

`.github/workflows/codescene-delta.yml` — **`workflow_dispatch` only**.

Run from the Actions tab or via CLI:

```bash
gh workflow run "CodeScene delta" \
  -f base_ref=origin/main \
  -f head_ref=HEAD
```

Uses `scripts/ci-codescene-delta.sh` which runs the `cs` CLI inside the `codescene/codescene-mcp` Docker image. No runtime download from `downloads.codescene.io` — the CLI is baked into the image at build time.

### Why Docker, not native install

`downloads.codescene.io` returns transient 403 errors on cold runners. The Docker image bundles the `cs` binary, avoiding the flaky download. See `scripts/ci-install-codescene-cli.sh` for the native installer (used locally, not in CI).

### PR Refactoring Agent

`.github/workflows/refactoring-agent.yml` — triggered by `/cs-agent` comments on PRs. Requires `CS_ACCESS_TOKEN` secret and at least one AI provider key (`ANTHROPIC_API_KEY`, `OPENAI_API_KEY`, etc.) plus `CS_AGENT_MODEL` variable.

## Local setup

### Option A: Docker (matches CI exactly)

```bash
export CS_ACCESS_TOKEN="your-pat-here"
bash scripts/ci-codescene-delta.sh main HEAD
```

This mounts the workspace into the container and runs `cs delta`.

### Option B: Native CLI install

```bash
bash scripts/ci-install-codescene-cli.sh
# Installs to ~/.local/bin/cs
export PATH="$HOME/.local/bin:$PATH"
cs delta origin/main HEAD --error-on-warnings
```

### Option C: Manual install

```bash
curl --proto '=https' --tlsv1.2 -fsSL \
  -H "Authorization: Bearer ${CS_ACCESS_TOKEN}" \
  -o /tmp/cs https://downloads.codescene.io/enterprise/cli/install-cs-tool.sh
bash /tmp/cs -y
```

The versioned download endpoint requires `CS_ACCESS_TOKEN` in the `Authorization` header (403 otherwise). The install script in the repo uses the redirect-stable "latest" channel instead.

## Key commands

```bash
cs version                                              # Verify install
cs delta origin/main HEAD --error-on-warnings           # PR delta (exit 1 on warnings)
cs delta origin/main HEAD                               # PR delta (warnings visible, non-fatal)
cs analyze <file>                                       # Single-file analysis
cs setup                                                # Interactive project setup
```

## Docker image

Image: `codescene/codescene-mcp` (Docker Hub). The image bundles:
- `cs` CLI (overridden entrypoint — we use `--entrypoint cs`)
- MCP server (not used by OpenADT, only the CLI)

Pin by digest in `scripts/ci-codescene-delta.sh` via `CODESCENE_MCP_IMAGE` env var.

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `CS_ACCESS_TOKEN org secret missing` | Branch protection blocks bot access, or secret not set | Trigger workflow manually; or add bot to trusted actors |
| `License check failed` / 403 from codescene.io | Expired PAT, wrong scope, or transient API error | Refresh PAT at <https://codescene.io/users/me/pat>, update org secret |
| `downloads.codescene.io` 403 on native install | Transient CDN issue or missing auth header | Use Docker path (`ci-codescene-delta.sh`) instead |
| `cs: command not found` | CLI not installed or not on PATH | Run `scripts/ci-install-codescene-cli.sh` or add `~/.local/bin` to PATH |

## Design ceilings (for agents)

When CodeScene findings are reported, fix to these targets:
- Function cyclomatic complexity ≤ 9 (hard cap); file mean CC ≤ 4.
- Function LoC ≤ 70; file LoC ≤ 1000.
- Nesting depth ≤ 4; Bumpy Road bumps ≤ 2 (depth ≥ 2).
- Function arguments ≤ 4; constructor arguments ≤ 5.
- Duplication: ≥ 10 LoC @ ≥ 75% similarity.

Run `bash scripts/ci-codescene-delta.sh origin/<baseRefName> HEAD` locally to verify before pushing.

## Files

| File | Purpose |
|------|---------|
| `.github/workflows/codescene-delta.yml` | Manual delta analysis workflow |
| `.github/workflows/refactoring-agent.yml` | `/cs-agent` PR refactoring agent |
| `scripts/ci-codescene-delta.sh` | Docker-based `cs delta` wrapper |
| `scripts/ci-install-codescene-cli.sh` | Native CLI installer (local use) |
