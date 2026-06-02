# Run OpenADT with SAP ADT SDK on classpath (direct com.sap.adt.* build).
$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$cli = Join-Path $repoRoot "apps\openadt-cli"
$configClasses = Join-Path $repoRoot "apps\openadt-config\target\classes"
$bootstrapClasses = Join-Path $repoRoot "apps\openadt-bootstrap\target\classes"
$classes = Join-Path $cli "target\classes"
$jar = @(Get-ChildItem (Join-Path $cli "target") -Filter "openadt-*.jar" -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch '(-sources|-javadoc)\.jar$' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1)
if ($jar) { $jar = $jar.FullName } else { $jar = Join-Path $cli "target\openadt.jar" }
$sapLib = Join-Path $cli "target\sap-lib"
$runtimeSapLib = Join-Path $env:USERPROFILE ".openadt\runtime\sap-lib"
$sapAdtClasses = Join-Path $repoRoot "apps\openadt-sap-adt\target\classes"
if (-not (Test-Path $jar) -and -not (Test-Path $classes)) {
    Write-Error "Build first: ./mvnw -q verify -f pom.xml -Pdistribution -Dopenadt.distribution=true (from repo root)"
}
$p2 = Join-Path $env:USERPROFILE ".p2\pool\plugins"
$sapBundlePattern = '^(com\.sap\.(adt|conn)|org\.(eclipse|osgi)\.)'
$sapJars = @()
if ((Test-Path $runtimeSapLib) -and ((Get-ChildItem $runtimeSapLib -Filter "*.jar").Count -ge 100)) {
    $sapJars = @(Get-ChildItem $runtimeSapLib -Filter "*.jar")
} elseif ((Test-Path $sapLib) -and ((Get-ChildItem $sapLib -Filter "*.jar").Count -ge 100)) {
    $sapJars = @(Get-ChildItem $sapLib -Filter "*.jar")
} elseif (Test-Path $p2) {
    $sapJars = @(Get-ChildItem $p2 -Filter "*.jar" | Where-Object { $_.Name -match $sapBundlePattern })
    Write-Warning "Using $($sapJars.Count) bundles from $p2 (Maven sap-lib is incomplete for adt discover)"
} else {
    $sapJars = @(Get-ChildItem $sapLib -Filter "*.jar" -ErrorAction SilentlyContinue)
}
$cp = @()
if (Test-Path $configClasses) { $cp += $configClasses }
if (Test-Path $bootstrapClasses) { $cp += $bootstrapClasses }
if (Test-Path $sapAdtClasses) { $cp += $sapAdtClasses }
if (Test-Path $classes) { $cp += $classes }
if (Test-Path $jar) { $cp += $jar }
function Resolve-CanonicalJcoJar([System.IO.FileInfo]$Jar) {
    if ($Jar.Name -notmatch '^(?:com\.sap\.conn\.jco[_-]|jco-)(\d+(?:\.\d+)+)\.jar$') {
        return $Jar.FullName
    }
    $canonical = "com.sap.conn.jco-$($Matches[1]).jar"
    if ($Jar.Name -ieq $canonical) {
        return $Jar.FullName
    }
    $cacheDir = Join-Path ([System.IO.Path]::GetTempPath()) "openadt-jco-lib"
    New-Item -ItemType Directory -Force -Path $cacheDir | Out-Null
    $dest = Join-Path $cacheDir $canonical
    $needsCopy = -not (Test-Path $dest)
    if (-not $needsCopy) {
        $existing = Get-Item $dest
        $needsCopy = $existing.Length -ne $Jar.Length -or $Jar.LastWriteTimeUtc -gt $existing.LastWriteTimeUtc
    }
    if ($needsCopy) {
        Copy-Item -LiteralPath $Jar.FullName -Destination $dest -Force
    }
    if ((Get-Item $dest).Length -ne $Jar.Length) {
        throw "Canonical JCo copy failed: $dest (expected $($Jar.Length) bytes)"
    }
    return $dest
}
$jcoCorePattern = '^(?:com\.sap\.conn\.jco_\d|jco-\d[\d.]*)\.jar$'
$jcoJars = @($sapJars | Where-Object { $_.Name -match $jcoCorePattern })
$nonJcoJars = @($sapJars | Where-Object { $_.Name -notmatch $jcoCorePattern })
$canonicalJco = $null
if ($jcoJars.Count -gt 0) {
    $jcoSource = @($jcoJars | Where-Object { $_.Name -match '^com\.sap\.conn\.jco[_-]' } | Sort-Object Name -Descending | Select-Object -First 1)
    if (-not $jcoSource) { $jcoSource = $jcoJars | Sort-Object Name -Descending | Select-Object -First 1 }
    $canonicalJco = Resolve-CanonicalJcoJar $jcoSource
    # JCo core must precede jco.eclipse on the classpath (SessionReferenceProvider lives in core JCo).
    $cp += $canonicalJco
}
$cp += ($nonJcoJars | ForEach-Object { $_.FullName })
$env:JAVA_HOME = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot" }
& "$env:JAVA_HOME\bin\java.exe" -cp ($cp -join ";") org.openadt.cli.OpenAdtCommand @args
