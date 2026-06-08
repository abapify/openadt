# Scoop post-install hook for OpenADT
# Runs with $dir = version install folder (contains openadt.jar)
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

Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "  openadt setup"
Write-Host "  openadt mcp serve --port 2236"
Write-Host "  openadt mcp print-config --port 2236"
Write-Host ""
Write-Host "For the standalone MCP binary (no Java, no Bun):" -ForegroundColor Cyan
Write-Host "  scoop install openadt-mcp"
Write-Host ""
