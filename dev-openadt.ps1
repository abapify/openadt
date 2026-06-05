# Dev launcher: run OpenADT from a local Maven build (not Scoop).
# Use explicitly: .\dev-openadt.ps1 — bare `openadt` must resolve to Scoop/Homebrew on PATH.
# Delegates to scripts/nx-openadt.ts (SDK classpath for snc, fat jar for --profile=sso|http).
param(
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]] $OpenAdtArgs
)

$ErrorActionPreference = "Stop"
$repoRoot = $PSScriptRoot
$launcher = Join-Path $repoRoot "scripts\nx-openadt.ts"

$bun = Get-Command bun -ErrorAction SilentlyContinue
if (-not $bun) {
  Write-Error "bun is required for .\dev-openadt.ps1. Install bun or use: .\scripts\openadt-sdk.ps1 @args"
  exit 1
}

& bun run $launcher @OpenAdtArgs
exit $LASTEXITCODE
