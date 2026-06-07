# Install global openadt-dev (~/.local/bin) — local clone CLI, not Scoop openadt.
# Builds openadt-dev.exe so MCP Inspector can spawn it (Windows: .cmd fails in Node).
param(
  [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"
$binDir = Join-Path $env:USERPROFILE ".local\bin"
New-Item -ItemType Directory -Force -Path $binDir | Out-Null

$devExe = Join-Path $binDir "openadt-dev.exe"
$devBin = Join-Path $RepoRoot "scripts\openadt-dev-bin.ts"
$mcpExe = Join-Path $binDir "openadt-mcp-dev.exe"
$mcpBin = Join-Path $RepoRoot "tools\sap-adt-mcp-launcher\src\mcp-dev-stdio-bin.ts"
$cmdShim = Join-Path $binDir "openadt-dev.cmd"

$bun = Join-Path $env:USERPROFILE ".bun\bin\bun.exe"
if (-not (Test-Path $bun)) {
  $found = Get-Command bun -ErrorAction SilentlyContinue
  if ($found) { $bun = $found.Source }
}
if (-not (Test-Path $bun)) {
  Write-Error "bun is required. Install from https://bun.sh"
}

Write-Host "Building openadt-dev.exe ..."
& $bun build --compile $devBin --outfile $devExe
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Building openadt-mcp-dev.exe (stdio shorthand, optional) ..."
& $bun build --compile $mcpBin --outfile $mcpExe
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$rootFile = Join-Path $binDir "openadt-dev.root"
Set-Content -Path $rootFile -Value $RepoRoot -Encoding ASCII -NoNewline
[Environment]::SetEnvironmentVariable("OPENADT_DEV_ROOT", $RepoRoot, "User")

@(
  "@echo off",
  "setlocal",
  "if not defined OPENADT_DEV_ROOT set `"OPENADT_DEV_ROOT=$RepoRoot`"",
  "call `"%OPENADT_DEV_ROOT%\dev-openadt.cmd`" %*",
  "exit /b %ERRORLEVEL%"
) | Set-Content -Path $cmdShim -Encoding ASCII

$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
if ($userPath -notlike "*$binDir*") {
  [Environment]::SetEnvironmentVariable("Path", "$userPath;$binDir", "User")
  Write-Host "Added $binDir to user PATH (new terminal to pick up)."
}

Write-Host ""
Write-Host "Dev CLI (local clone, same flags as Scoop openadt):"
Write-Host "  openadt-dev.exe mcp serve --stdio"
Write-Host ""
Write-Host "MCP Inspector (Transport STDIO):"
Write-Host "  Command: $devExe"
Write-Host "  Args:    mcp serve --stdio"
Write-Host ""
Write-Host "OPENADT_DEV_ROOT (clone): $RepoRoot"
