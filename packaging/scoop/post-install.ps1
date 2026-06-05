# Scoop post-install hook for OpenADT
# Runs with $dir = version install folder (contains openadt.jar, sap-adt-mcp-launcher/)
param(
    [Parameter(Mandatory = $true)]
    [string] $InstallDir
)

Write-Host ""
Write-Host "OpenADT installed to $InstallDir" -ForegroundColor Green

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "Warning: Java not on PATH (required)." -ForegroundColor Yellow
    Write-Host "  scoop bucket add java; scoop install openjdk21" -ForegroundColor Yellow
} else {
    $javaLine = (java -version 2>&1 | Select-Object -First 1).ToString()
    Write-Host "Java: $javaLine"
}

if (-not (Get-Command bun -ErrorAction SilentlyContinue)) {
    Write-Host "Note: openadt mcp requires Bun on PATH." -ForegroundColor Yellow
    Write-Host "  scoop install bun" -ForegroundColor Yellow
} else {
    $bunVer = (bun --version 2>&1).ToString().Trim()
    Write-Host "Bun: $bunVer (mcp ready)"
}

$launcher = Join-Path $InstallDir "sap-adt-mcp-launcher\src\main.ts"
if (-not (Test-Path -LiteralPath $launcher)) {
    Write-Host "Warning: MCP launcher not in this build (upgrade to openadt 1.3.1+ for openadt mcp)." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "  openadt setup"
Write-Host "  openadt mcp serve --port 2236"
Write-Host "  openadt mcp print-config --port 2236"
Write-Host ""
