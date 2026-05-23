param(
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]] $OpenAdtArgs
)

$ErrorActionPreference = "Stop"
$OpenAdtHome = if ($env:OPENADT_HOME) { $env:OPENADT_HOME } else { Split-Path -Parent $PSScriptRoot }
$LiteJar = Join-Path $OpenAdtHome "openadt.jar"
$FullJar = Join-Path $env:USERPROFILE ".openadt/runtime/openadt-full.jar"
$LauncherRoot = $PSScriptRoot

function Get-JavaExe {
  if ($env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME "bin\java.exe"
    if (Test-Path $candidate) { return $candidate }
  }
  $fromPath = Get-Command java -ErrorAction SilentlyContinue
  if ($fromPath) { return $fromPath.Source }
  $candidates = @(
    "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\bin\java.exe",
    "C:\Program Files\Eclipse Adoptium\jdk-21*\bin\java.exe",
    "C:\Program Files\Java\jdk-21*\bin\java.exe"
  )
  foreach ($pattern in $candidates) {
    $match = Get-Item $pattern -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($match) { return $match.FullName }
  }
  return $null
}

function Invoke-LiteOpenAdt {
  param([string[]] $Args)
  $javaExe = Get-JavaExe
  if (-not $javaExe) {
    Write-Error "java not found on PATH; install JDK 21 (for example Temurin or Microsoft Build of OpenJDK)"
  }
  & $javaExe -jar $LiteJar @Args
  exit $LASTEXITCODE
}

function Resolve-CanonicalJcoJar([System.IO.FileInfo]$Jar) {
  if ($Jar.Name -notmatch '^(?:com\.sap\.conn\.jco[_-]|jco-)(\d+(?:\.\d+)+)\.jar$') {
    return $Jar.FullName
  }
  $canonical = "com.sap.conn.jco-$($Matches[1]).jar"
  if ($Jar.Name -ieq $canonical) { return $Jar.FullName }
  $cacheDir = Join-Path ([System.IO.Path]::GetTempPath()) "openadt-jco-lib"
  New-Item -ItemType Directory -Force -Path $cacheDir | Out-Null
  $dest = Join-Path $cacheDir $canonical
  if (-not (Test-Path $dest) -or (Get-Item $dest).Length -ne $Jar.Length) {
    Copy-Item -LiteralPath $Jar.FullName -Destination $dest -Force
  }
  return $dest
}

function Invoke-SdkOpenAdt {
  param([string[]] $Args)
  if (-not (Test-Path $FullJar)) {
    Write-Host "Full SAP SDK runtime is not prepared yet." -ForegroundColor Yellow
    Write-Host "Run: openadt config build" -ForegroundColor Yellow
    Write-Host "  or: openadt setup" -ForegroundColor Yellow
    exit 1
  }
  $configPath = Join-Path $env:USERPROFILE ".openadt/config.toml"
  $adtPluginsDir = $null
  if (Test-Path $configPath) {
    $match = Select-String -Path $configPath -Pattern '^\s*adt_plugins_dir\s*=\s*"([^"]+)"' | Select-Object -First 1
    if ($match) { $adtPluginsDir = $match.Matches[0].Groups[1].Value }
  }
  if (-not $adtPluginsDir) {
    $adtPluginsDir = Join-Path $env:USERPROFILE ".p2/pool/plugins"
  }
  if (-not (Test-Path $adtPluginsDir)) {
    Write-Error "ADT plugins directory not found. Run 'openadt config bootstrap' or 'openadt setup' first."
  }
  $sapJars = @(Get-ChildItem $adtPluginsDir -Filter "*.jar" | Where-Object {
    $_.Name -match '^(com\.sap\.(adt|conn)|org\.eclipse\.)'
  })
  if ($sapJars.Count -eq 0) {
    Write-Error "No SAP ADT bundles in $adtPluginsDir"
  }
  $cp = @($FullJar)
  $jcoCorePattern = '^(?:com\.sap\.conn\.jco_\d|jco-\d[\d.]*)\.jar$'
  $jcoJars = @($sapJars | Where-Object { $_.Name -match $jcoCorePattern })
  $nonJcoJars = @($sapJars | Where-Object { $_.Name -notmatch $jcoCorePattern })
  if ($jcoJars.Count -gt 0) {
    $jcoSource = @($jcoJars | Where-Object { $_.Name -match '^com\.sap\.conn\.jco[_-]' } | Sort-Object Name -Descending | Select-Object -First 1)
    if (-not $jcoSource) { $jcoSource = $jcoJars | Sort-Object Name -Descending | Select-Object -First 1 }
    $cp += (Resolve-CanonicalJcoJar $jcoSource)
  }
  $cp += ($nonJcoJars | ForEach-Object { $_.FullName })
  $javaExe = Get-JavaExe
  if (-not $javaExe) {
    Write-Error "java not found on PATH; install JDK 21"
  }
  & $javaExe -cp ($cp -join ";") org.openadt.cli.OpenAdtCommand @Args
  exit $LASTEXITCODE
}

$env:OPENADT_HOME = $OpenAdtHome
if ($OpenAdtArgs.Count -eq 0) {
  Invoke-LiteOpenAdt @OpenAdtArgs
}

$subcommand = $OpenAdtArgs[0]
if ($subcommand -in @("fetch", "proxy")) {
  Invoke-SdkOpenAdt @OpenAdtArgs
}

Invoke-LiteOpenAdt @OpenAdtArgs
