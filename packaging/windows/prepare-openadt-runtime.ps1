param(
  [Parameter(Mandatory = $true)]
  [string] $Version,
  [Parameter(Mandatory = $true)]
  [string] $AdtPluginsDir
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $AdtPluginsDir)) {
  Write-Error "ADT plugins directory not found: $AdtPluginsDir"
}

$runtimeDir = Join-Path $env:USERPROFILE ".openadt/runtime"
$outJar = Join-Path $runtimeDir "openadt-full.jar"
$buildRoot = Join-Path $env:LOCALAPPDATA "openadt/build"
$sourceDir = Join-Path $buildRoot "openadt-$Version"
$zipPath = Join-Path $buildRoot "openadt-$Version.zip"
$tag = "v$Version"

New-Item -ItemType Directory -Force -Path $runtimeDir, $buildRoot | Out-Null

if (Test-Path (Join-Path $runtimeDir "version.txt")) {
  $preparedVersion = (Get-Content (Join-Path $runtimeDir "version.txt") -Raw).Trim()
  if ($preparedVersion -eq $Version -and (Test-Path $outJar)) {
    Write-Host "Runtime jar already prepared: $outJar"
    exit 0
  }
}

if (-not (Test-Path (Join-Path $sourceDir "apps/openadt-cli/mvnw.cmd"))) {
  Write-Host "Downloading OpenADT $tag source..."
  $url = "https://github.com/abapify/openadt/archive/refs/tags/$tag.zip"
  Invoke-WebRequest -Uri $url -OutFile $zipPath
  if (Test-Path $sourceDir) { Remove-Item -Recurse -Force $sourceDir }
  Expand-Archive -Path $zipPath -DestinationPath $buildRoot -Force
  $extracted = Get-ChildItem $buildRoot -Directory -Filter "openadt-*" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
  if (-not $extracted) {
    Write-Error "Downloaded archive did not contain an openadt-* directory."
  }
  $sourceDir = $extracted.FullName
}

$cliDir = Join-Path $sourceDir "apps/openadt-cli"
Push-Location $cliDir
try {
  Write-Host "Building full OpenADT runtime jar (first run may take a few minutes)..."
  ./mvnw.cmd -q package "-Dmaven.test.skip=true" "-Dadt.plugins.dir=$AdtPluginsDir"
  $built = Get-ChildItem "target/openadt-*.jar" |
    Where-Object { $_.Name -notmatch '-sources|-javadoc' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
  if (-not $built) {
    Write-Error "Maven build did not produce openadt-*.jar in target/"
  }
  Copy-Item -LiteralPath $built.FullName -Destination $outJar -Force
  Set-Content -Path (Join-Path $runtimeDir "version.txt") -Value $Version
  Write-Host "Prepared runtime jar: $outJar"
} finally {
  Pop-Location
}
