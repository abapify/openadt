# Scoop post-install hook for openadt-mcp (compiled Bun binary)
# Runs with $dir = version install folder (contains openadt-mcp.exe)
param(
    [Parameter(Mandatory = $true)]
    [string] $InstallDir
)

Write-Host ""
Write-Host "openadt-mcp installed to $InstallDir" -ForegroundColor Green

# openadt-mcp itself has no Java requirement. adt-lsc (SAP ADT for VS Code)
# bundles its own JRE, so JDK 21 is only needed if you wire openadt-mcp up
# to an adt-lsc install that needs an external runtime (e.g. SNC).
$jdks = Get-Command java -ErrorAction SilentlyContinue
if (-not $jdks) {
    Write-Host "Note: Java not on PATH." -ForegroundColor Yellow
    Write-Host "  Only required for adt-lsc's bundled JRE/SNC; openadt-mcp itself runs without Java." -ForegroundColor Yellow
} else {
    $javaLine = (java -version 2>&1 | Select-Object -First 1).ToString()
    Write-Host "Java: $javaLine"
}

Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "  openadt-mcp serve --port 2236"
Write-Host "  openadt-mcp serve --stdio        # for MCP clients that talk stdio"
Write-Host "  openadt-mcp print-config --port 2236"
Write-Host ""
