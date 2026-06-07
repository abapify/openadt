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

$bunPath = $env:OPENADT_BUN
if (-not $bunPath) {
  $candidate = Join-Path $env:USERPROFILE ".bun\bin\bun.exe"
  if (Test-Path $candidate) {
    $bunPath = $candidate
  } else {
    $cmd = Get-Command bun -ErrorAction SilentlyContinue
    if ($cmd) {
      $bunPath = $cmd.Source
    }
  }
}
if (-not $bunPath) {
  Write-Error "bun is required for .\dev-openadt.ps1. Install bun: https://bun.sh or set OPENADT_BUN."
  exit 1
}

& $bunPath $launcher @OpenAdtArgs
exit $LASTEXITCODE
