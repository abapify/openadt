param(
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]] $OpenAdtArgs
)

$ErrorActionPreference = "Stop"

if ((-not $OpenAdtArgs -or $OpenAdtArgs.Count -eq 0) -and $env:OPENADT_ARG_COUNT) {
  $count = [int]$env:OPENADT_ARG_COUNT
  $OpenAdtArgs = @()
  for ($i = 0; $i -lt $count; $i++) {
    $OpenAdtArgs += (Get-ChildItem -Path "Env:OPENADT_ARG_$i").Value
  }
}

$OpenAdtHome = if ($env:OPENADT_HOME) { $env:OPENADT_HOME } else { Split-Path -Parent $PSScriptRoot }
$LiteJar = Join-Path $OpenAdtHome "openadt.jar"
$FullJar = Join-Path $env:USERPROFILE ".openadt/runtime/openadt-full.jar"
$VersionFile = Join-Path $OpenAdtHome "VERSION"

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
  param([string[]] $CliArgs)
  $javaExe = Get-JavaExe
  if (-not $javaExe) {
    Write-Error "java not found on PATH; install JDK 21 (for example Temurin or Microsoft Build of OpenJDK)"
  }
  & $javaExe -jar $LiteJar @CliArgs
  exit $LASTEXITCODE
}

function Resolve-CanonicalJcoJar([System.IO.FileInfo]$Jar) {
  $jcoNamePattern = @'
^(?:com\.sap\.conn\.jco[_-]|jco-)(\d+(?:\.\d+)+)\.jar$
'@
  if ($Jar.Name -notmatch $jcoNamePattern) {
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

function Get-OpenAdtInstallVersion {
  if (Test-Path $VersionFile) {
    return (Get-Content $VersionFile -Raw).Trim()
  }
  return "1.0.0"
}

function Ensure-SdkRuntimePrepared {
  param([string] $AdtPluginsDir)
  if (Test-Path $FullJar) {
    $marker = Join-Path $env:USERPROFILE ".openadt/runtime/version.txt"
    if ((Test-Path $marker) -and ((Get-Content $marker -Raw).Trim() -eq (Get-OpenAdtInstallVersion))) {
      return
    }
  }
  if (-not $AdtPluginsDir -or -not (Test-Path $AdtPluginsDir)) {
    Write-Error "ADT plugins directory not found. Run 'openadt setup' or 'openadt config bootstrap' first."
  }
  $prepareScript = Join-Path $OpenAdtHome "bin/prepare-openadt-runtime.ps1"
  if (-not (Test-Path $prepareScript)) {
    Write-Error "Missing $prepareScript - reinstall OpenADT from the release zip."
  }
  Write-Host "Preparing SAP SDK runtime (first fetch/proxy may take several minutes)..." -ForegroundColor Yellow
  & $prepareScript -Version (Get-OpenAdtInstallVersion) -AdtPluginsDir $AdtPluginsDir
  if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
  }
}

function Invoke-SdkOpenAdt {
  param([string[]] $CliArgs)
  $configPath = Join-Path $env:USERPROFILE ".openadt/config.toml"
  $adtPluginsDir = $null
  if (Test-Path $configPath) {
    foreach ($line in Get-Content -LiteralPath $configPath) {
      $trimmed = $line.Trim()
      if ($trimmed.StartsWith('adt_plugins_dir') -and $trimmed.Contains('=')) {
        $value = $trimmed.Substring($trimmed.IndexOf('=') + 1).Trim()
        if ($value.StartsWith('"') -and $value.EndsWith('"') -and $value.Length -ge 2) {
          $adtPluginsDir = $value.Substring(1, $value.Length - 2)
          break
        }
      }
    }
  }
  if (-not $adtPluginsDir) {
    $adtPluginsDir = Join-Path $env:USERPROFILE ".p2/pool/plugins"
  }
  if (-not (Test-Path $adtPluginsDir)) {
    Write-Error "ADT plugins directory not found. Run 'openadt config bootstrap' or 'openadt setup' first."
  }
  Ensure-SdkRuntimePrepared -AdtPluginsDir $adtPluginsDir
  $runtimeSapLib = Join-Path $env:USERPROFILE ".openadt/runtime/sap-lib"
  if ((Test-Path $runtimeSapLib) -and ((Get-ChildItem $runtimeSapLib -Filter "*.jar").Count -ge 100)) {
    $sapJars = @(Get-ChildItem $runtimeSapLib -Filter "*.jar")
  } else {
    $sapBundlePattern = @'
^(com\.sap\.(adt|conn)|org\.(eclipse|osgi)\.)
'@
    $sapJars = @(Get-ChildItem $adtPluginsDir -Filter "*.jar" | Where-Object {
      $_.Name -match $sapBundlePattern
    })
  }
  if ($sapJars.Count -eq 0) {
    Write-Error "No SAP ADT bundles in $adtPluginsDir"
  }
  if (-not (Test-Path $FullJar)) {
    Write-Error "SDK runtime jar missing at $FullJar. Run: openadt setup  (or: openadt config build)"
  }
  # Full runtime jar is a complete shaded CLI including DiscoveryService/LogonService; lite jar must not precede it on the classpath.
  $cp = @($FullJar)
  $jcoCorePattern = @'
^(?:com\.sap\.conn\.jco_\d|jco-\d[\d.]*)\.jar$
'@
  $jcoSourcePattern = @'
^com\.sap\.conn\.jco[_-]
'@
  $jcoJars = @($sapJars | Where-Object { $_.Name -match $jcoCorePattern })
  $nonJcoJars = @($sapJars | Where-Object { $_.Name -notmatch $jcoCorePattern })
  if ($jcoJars.Count -gt 0) {
    $jcoSource = @($jcoJars | Where-Object { $_.Name -match $jcoSourcePattern } | Sort-Object Name -Descending | Select-Object -First 1)
    if (-not $jcoSource) { $jcoSource = $jcoJars | Sort-Object Name -Descending | Select-Object -First 1 }
    $cp += (Resolve-CanonicalJcoJar $jcoSource)
  }
  $cp += ($nonJcoJars | ForEach-Object { $_.FullName })
  $javaExe = Get-JavaExe
  if (-not $javaExe) {
    Write-Error "java not found on PATH; install JDK 21"
  }
  $argFile = Join-Path $env:TEMP ("openadt-" + [guid]::NewGuid().ToString("N") + ".args")
  try {
    $lines = New-Object System.Collections.Generic.List[string]
    [void]$lines.Add("-cp")
    [void]$lines.Add($cp -join ";")
    [void]$lines.Add("org.openadt.cli.OpenAdtCommand")
    foreach ($a in $CliArgs) { [void]$lines.Add($a) }
    [IO.File]::WriteAllLines($argFile, $lines)
    & $javaExe "@$argFile"
    exit $LASTEXITCODE
  } finally {
    Remove-Item -LiteralPath $argFile -Force -ErrorAction SilentlyContinue
  }
}

function Test-OpenAdtProxyActive {
  param([string]$Alias)
  if (-not $Alias) { return $false }
  $allowed = 'abcdefghijklmnopqrstuvwxyz0123456789._-'
  $safe = -join ($Alias.ToLower().ToCharArray() | ForEach-Object {
    if ($allowed.IndexOf($_) -ge 0) { $_ } else { '_' }
  })
  $reg = Join-Path $env:USERPROFILE ".openadt/runtime/proxy-$safe.json"
  if (-not (Test-Path $reg)) { return $false }
  try {
    $info = Get-Content $reg -Raw | ConvertFrom-Json
    if (-not $info.port) { return $false }
    $client = New-Object System.Net.Sockets.TcpClient
    $async = $client.BeginConnect($info.host, [int]$info.port, $null, $null)
    $ok = $async.AsyncWaitHandle.WaitOne(500)
    if ($ok) { $client.EndConnect($async) }
    $client.Close()
    return $ok
  } catch {
    return $false
  }
}

function Resolve-FetchUsesLiteProxy {
  param([string[]]$CliArgs)
  if ($CliArgs -contains "--direct") { return $false }
  for ($i = 0; $i -lt $CliArgs.Count; $i++) {
    if ($CliArgs[$i] -eq "fetch" -and ($i + 1) -lt $CliArgs.Count) {
      return (Test-OpenAdtProxyActive $CliArgs[$i + 1])
    }
  }
  return $false
}

$env:OPENADT_HOME = $OpenAdtHome
if (-not $OpenAdtArgs -or $OpenAdtArgs.Count -eq 0) {
  Invoke-LiteOpenAdt @()
}

$subcommand = $OpenAdtArgs[0]
if ($subcommand -eq "fetch" -and (Resolve-FetchUsesLiteProxy $OpenAdtArgs)) {
  Invoke-LiteOpenAdt $OpenAdtArgs
}

if ($subcommand -in @("fetch", "proxy", "adt")) {
  Invoke-SdkOpenAdt $OpenAdtArgs
}

Invoke-LiteOpenAdt $OpenAdtArgs
