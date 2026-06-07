# Install global openadt-dev (~/.local/bin) — local clone CLI, not Scoop openadt.
# Builds openadt-dev.exe / openadt-mcp-dev.exe so MCP Inspector can spawn them
# (Windows: .cmd fails in Node). The .exe is a THIN re-exec shim (dev-exe-shim.ts)
# that runs the SOURCE entry via bun at runtime, so launcher/tool edits are picked
# up WITHOUT rebuilding the .exe. Only re-run this installer if the shim itself or
# the bun runtime changes.
param(
  [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"
$binDir = Join-Path $env:USERPROFILE ".local\bin"
New-Item -ItemType Directory -Force -Path $binDir | Out-Null

$devExe = Join-Path $binDir "openadt-dev.exe"
$mcpExe = Join-Path $binDir "openadt-mcp-dev.exe"
$shim = Join-Path $RepoRoot "scripts\dev-exe-shim.ts"
$cmdShim = Join-Path $binDir "openadt-dev.cmd"

$bun = Join-Path $env:USERPROFILE ".bun\bin\bun.exe"
if (-not (Test-Path $bun)) {
  $found = Get-Command bun -ErrorAction SilentlyContinue
  if ($found) { $bun = $found.Source }
}
if (-not (Test-Path $bun)) {
  Write-Error "bun is required. Install from https://bun.sh"
}

# Both exes are the SAME thin shim; it picks the source entry by its own name
# (…mcp… → MCP stdio launcher, else the dev CLI). Logic stays in source.
Write-Host "Building openadt-dev.exe (thin shim → source) ..."
& $bun build --compile $shim --outfile $devExe
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Building openadt-mcp-dev.exe (thin shim → source) ..."
& $bun build --compile $shim --outfile $mcpExe
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
