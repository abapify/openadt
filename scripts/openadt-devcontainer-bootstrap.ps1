param(
    [string]$SourceRoot,
    [string]$JcoArchive,
    [string]$CryptoArchive,
    [string]$SapcarPath,
    [string]$ContainerWorkspace,
    [switch]$NonInteractive
)

$ErrorActionPreference = 'Stop'

function Get-WindowsHome {
    if ($env:USERPROFILE) {
        return $env:USERPROFILE
    }
    return [Environment]::GetFolderPath('UserProfile')
}

function Get-RepoRoot {
    return (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
}

function Resolve-DefaultSourceRoot {
    return Join-Path (Get-WindowsHome) '.openadt\dist'
}

function Resolve-ContainerWorkspace {
    param([string]$RepoRoot, [string]$Override)

    if ($Override) {
        return $Override
    }

    $repoName = Split-Path $RepoRoot -Leaf
    return "/workspaces/$repoName"
}

function Prompt-Value {
    param(
        [string]$Prompt,
        [string]$Default = ''
    )

    if ($Default) {
        $value = Read-Host "$Prompt [$Default]"
        if ([string]::IsNullOrWhiteSpace($value)) {
            return $Default
        }
        return $value
    }
    return (Read-Host $Prompt)
}

function Get-CandidateFile {
    param(
        [string]$Root,
        [string]$Pattern
    )

    if (-not $Root -or -not (Test-Path $Root)) {
        return $null
    }

    return Get-ChildItem -Path $Root -Filter $Pattern -File -Recurse -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending |
        Select-Object -First 1
}

function Resolve-InputFile {
    param(
        [string]$ExplicitPath,
        [string]$SourceRoot,
        [string]$Pattern,
        [string]$PromptLabel,
        [switch]$NonInteractive
    )

    if ($ExplicitPath) {
        if (-not (Test-Path $ExplicitPath)) {
            throw "$PromptLabel not found: $ExplicitPath"
        }
        return (Resolve-Path $ExplicitPath).Path
    }

    $candidate = Get-CandidateFile -Root $SourceRoot -Pattern $Pattern
    if ($candidate) {
        return $candidate.FullName
    }

    if ($NonInteractive) {
        throw "$PromptLabel matching '$Pattern' not found under $SourceRoot"
    }

    $entered = Prompt-Value -Prompt "Enter path to $PromptLabel"
    if (-not $entered) {
        throw "$PromptLabel path is required"
    }
    if (-not (Test-Path $entered)) {
        throw "$PromptLabel not found: $entered"
    }
    return (Resolve-Path $entered).Path
}

function Find-ExtractedFile {
    param(
        [string]$Root,
        [string]$FileName,
        [int]$Retries = 10,
        [int]$DelayMs = 300
    )

    for ($attempt = 0; $attempt -lt $Retries; $attempt++) {
        $match = Get-ChildItem -Path $Root -File -Recurse -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -eq $FileName } |
            Select-Object -First 1
        if ($match) {
            return $match.FullName
        }
        Start-Sleep -Milliseconds $DelayMs
    }
    return $null
}

function New-CleanDirectory {
    param([string]$Path)

    if (Test-Path $Path) {
        Remove-Item -Recurse -Force $Path
    }
    New-Item -ItemType Directory -Path $Path | Out-Null
}

function Write-Utf8NoBomFile {
    param(
        [string]$Path,
        [string]$Content
    )

    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Content, $encoding)
}

function Expand-JcoArchive {
    param(
        [string]$ArchivePath,
        [string]$TempRoot
    )

    $zipExtract = Join-Path $TempRoot 'jco-zip'
    $tgzExtract = Join-Path $TempRoot 'jco-tgz'
    New-CleanDirectory $zipExtract
    New-CleanDirectory $tgzExtract

    Expand-Archive -Path $ArchivePath -DestinationPath $zipExtract -Force
    $tgz = Get-ChildItem -Path $zipExtract -Filter '*.tgz' -File -Recurse | Select-Object -First 1
    if (-not $tgz) {
        throw "No .tgz payload found inside JCo archive: $ArchivePath"
    }

    & tar.exe -xzf $tgz.FullName -C $tgzExtract

    $jar = Find-ExtractedFile -Root $tgzExtract -FileName 'sapjco3.jar'
    $native = Find-ExtractedFile -Root $tgzExtract -FileName 'libsapjco3.so'
    if (-not $jar) {
        throw "sapjco3.jar not found after extracting $ArchivePath"
    }
    if (-not $native) {
        throw "libsapjco3.so not found after extracting $ArchivePath"
    }

    return [PSCustomObject]@{
        Jar = $jar
        Native = $native
    }
}

function Expand-CryptoArchive {
    param(
        [string]$ArchivePath,
        [string]$SapcarPath,
        [string]$TempRoot
    )

    $cryptoExtract = Join-Path $TempRoot 'crypto'
    New-CleanDirectory $cryptoExtract

    $null = & $SapcarPath -xvf $ArchivePath -R $cryptoExtract

    $crypto = Find-ExtractedFile -Root $cryptoExtract -FileName 'libsapcrypto.so'
    if (-not $crypto) {
        throw "libsapcrypto.so not found after extracting $ArchivePath"
    }
    return $crypto
}

function Stage-Runtime {
    param(
        [string]$RepoRoot,
        [pscustomobject]$JcoFiles,
        [string]$CryptoSo
    )

    $stageRoot = Join-Path $RepoRoot '.devcontainer\dist'
    $jcoStage = Join-Path $stageRoot 'jco'
    $sncStage = Join-Path $stageRoot 'snc'
    $metadataStage = Join-Path $stageRoot 'metadata'

    New-CleanDirectory $jcoStage
    New-CleanDirectory $sncStage
    New-CleanDirectory $metadataStage

    Copy-Item -Force $JcoFiles.Jar (Join-Path $jcoStage 'sapjco3.jar')
    Copy-Item -Force $JcoFiles.Native (Join-Path $jcoStage 'libsapjco3.so')
    Copy-Item -Force $CryptoSo (Join-Path $sncStage 'libsapcrypto.so')

    return [PSCustomObject]@{
        StageRoot = $stageRoot
        JcoJar = (Join-Path $jcoStage 'sapjco3.jar')
        JcoNativeDir = $jcoStage
        Sapcrypto = (Join-Path $sncStage 'libsapcrypto.so')
        MetadataDir = $metadataStage
    }
}

function Write-Manifest {
    param(
        [string]$ManifestPath,
        [string]$ContainerWorkspace,
        [string]$JcoArchive,
        [string]$CryptoArchive,
        [string]$SapcarPath,
        [pscustomobject]$Stage
    )

    $manifest = [ordered]@{
        generated_at_utc = [DateTime]::UtcNow.ToString('o')
        container_workspace = $ContainerWorkspace
        source = [ordered]@{
            jco_archive = $JcoArchive
            crypto_archive = $CryptoArchive
            sapcar = $SapcarPath
        }
        staged = [ordered]@{
            jco_jar = $Stage.JcoJar
            jco_native_dir = $Stage.JcoNativeDir
            sapcrypto = $Stage.Sapcrypto
        }
    }
    Write-Utf8NoBomFile -Path $ManifestPath -Content ($manifest | ConvertTo-Json -Depth 5)
}

function Update-ConfigRuntime {
    param(
        [string]$ConfigPath,
        [string]$ContainerWorkspace
    )

    $configDir = Split-Path $ConfigPath -Parent
    if (-not (Test-Path $configDir)) {
        New-Item -ItemType Directory -Path $configDir | Out-Null
    }

    $runtimeBlock = @"
[runtime]
jco_jar = "$ContainerWorkspace/.devcontainer/dist/jco/sapjco3.jar"
jco_native_dir = "$ContainerWorkspace/.devcontainer/dist/jco"
sapcrypto = "$ContainerWorkspace/.devcontainer/dist/snc/libsapcrypto.so"
"@

    if (-not (Test-Path $ConfigPath)) {
        Write-Utf8NoBomFile -Path $ConfigPath -Content @"
version = 1

$runtimeBlock
"@
        return
    }

    $content = Get-Content -Path $ConfigPath -Raw -Encoding UTF8
    $content = [regex]::Replace($content, '(?ms)^\[runtime\]\r?\n.*?(?=^\[|\z)', '')
    $content = $content.Trim()

    $lines = if ($content) { $content -split "\r?\n" } else { @() }
    $firstTableIndex = -1
    for ($i = 0; $i -lt $lines.Length; $i++) {
        if ($lines[$i] -match '^\[') {
            $firstTableIndex = $i
            break
        }
    }

    if ($firstTableIndex -ge 0) {
        $header = ($lines[0..($firstTableIndex - 1)] -join "`n").Trim()
        $tables = ($lines[$firstTableIndex..($lines.Length - 1)] -join "`n").Trim()
    } else {
        $header = ($lines -join "`n").Trim()
        $tables = ''
    }

    if ($header -notmatch '(?m)^version\s*=') {
        if ($header) {
            $header = "version = 1`n`n$header"
        } else {
            $header = 'version = 1'
        }
    }

    $parts = @($header.Trim(), $runtimeBlock.Trim())
    if ($tables) {
        $parts += $tables.Trim()
    }
    Write-Utf8NoBomFile -Path $ConfigPath -Content ((($parts -join "`n`n").Trim()) + "`n")
}

$repoRoot = Get-RepoRoot
if (-not $SourceRoot) {
    $SourceRoot = Resolve-DefaultSourceRoot
}
if (-not (Test-Path $SourceRoot)) {
    if ($NonInteractive) {
        throw "Source root not found: $SourceRoot"
    }
    $SourceRoot = Prompt-Value -Prompt 'Enter source folder with SAP archives' -Default $SourceRoot
}

$containerWorkspace = Resolve-ContainerWorkspace -RepoRoot $repoRoot -Override $ContainerWorkspace
$jcoArchivePath = Resolve-InputFile -ExplicitPath $JcoArchive -SourceRoot $SourceRoot -Pattern 'sapjco31*.zip' -PromptLabel 'Linux JCo archive' -NonInteractive:$NonInteractive
$cryptoArchivePath = Resolve-InputFile -ExplicitPath $CryptoArchive -SourceRoot $SourceRoot -Pattern 'SAPCRYPTOLIB*.SAR' -PromptLabel 'Linux CryptoLib SAR archive' -NonInteractive:$NonInteractive
$sapcarExePath = Resolve-InputFile -ExplicitPath $SapcarPath -SourceRoot $SourceRoot -Pattern 'SAPCAR*.EXE' -PromptLabel 'SAPCAR executable' -NonInteractive:$NonInteractive

$tempRoot = Join-Path $repoRoot 'tmp\devcontainer-bootstrap'
if (Test-Path $tempRoot) {
    Remove-Item -Recurse -Force $tempRoot
}
New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null
try {
    $jcoFiles = Expand-JcoArchive -ArchivePath $jcoArchivePath -TempRoot $tempRoot
    $cryptoSo = Expand-CryptoArchive -ArchivePath $cryptoArchivePath -SapcarPath $sapcarExePath -TempRoot $tempRoot
    $stage = Stage-Runtime -RepoRoot $repoRoot -JcoFiles $jcoFiles -CryptoSo $cryptoSo

    $manifestPath = Join-Path $stage.MetadataDir 'manifest.json'
    Write-Manifest -ManifestPath $manifestPath -ContainerWorkspace $containerWorkspace -JcoArchive $jcoArchivePath -CryptoArchive $cryptoArchivePath -SapcarPath $sapcarExePath -Stage $stage

    $configPath = Join-Path $repoRoot '.openadt\config.toml'
    Update-ConfigRuntime -ConfigPath $configPath -ContainerWorkspace $containerWorkspace

    Write-Output "Staged Linux SAP runtime under $($stage.StageRoot)"
    Write-Output "Updated config runtime block in $configPath"
    Write-Output "Manifest written to $manifestPath"
} finally {
    if (Test-Path $tempRoot) {
        try {
            Remove-Item -Recurse -Force $tempRoot -ErrorAction Stop
        } catch {
            Write-Warning "Failed to clean temporary bootstrap directory: $tempRoot"
        }
    }
}
