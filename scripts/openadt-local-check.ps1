param(
    [string]$SystemAlias = 'DEV'
)

$ErrorActionPreference = 'Stop'

function New-CheckResult {
    param(
        [string]$Name,
        [string]$Status,
        [string]$Detail,
        [string]$Action = ''
    )

    [PSCustomObject]@{
        Name = $Name
        Status = $Status
        Detail = $Detail
        Action = $Action
    }
}

function Get-WindowsHome {
    if ($env:USERPROFILE) {
        return $env:USERPROFILE
    }
    return [Environment]::GetFolderPath('UserProfile')
}

function Get-LandscapePaths {
    $userHome = Get-WindowsHome
    @(
        (Join-Path $userHome 'AppData\Roaming\SAP\Common\SAPUILandscape.xml')
        (Join-Path $userHome 'AppData\Roaming\SAP\LogonServerConfigCache')
    )
}

function Get-NwbcRecentsDir {
    $userHome = Get-WindowsHome
    Join-Path $userHome 'AppData\Roaming\SAP\NWBC\Recents'
}

function Get-JcoJar {
    $userHome = Get-WindowsHome
    $pluginsDir = Join-Path $userHome '.p2\pool\plugins'
    if (-not (Test-Path $pluginsDir)) {
        return $null
    }

    Get-ChildItem -Path $pluginsDir -Filter 'com.sap.conn.jco_*.jar' -File -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        Select-Object -First 1
}

function Get-FirstFile {
    param(
        [string[]]$Roots,
        [string[]]$Patterns,
        [int]$Depth = 6
    )

    foreach ($root in $Roots) {
        if (-not (Test-Path $root)) {
            continue
        }
        foreach ($pattern in $Patterns) {
            $item = Get-ChildItem -Path $root -Filter $pattern -File -Recurse -Depth $Depth -ErrorAction SilentlyContinue |
                Select-Object -First 1
            if ($item) {
                return $item
            }
        }
    }
    return $null
}

function Get-SecureLoginInstallDir {
    $candidates = @(
        'C:\Program Files\SAP\FrontEnd\SecureLogin'
        'C:\Program Files (x86)\SAP\FrontEnd\SecureLogin'
    )
    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return $candidate
        }
    }
    return $null
}

function Test-SecureLoginHub {
    $uri = 'https://127.0.0.1:34443/'
    $originalCallback = [System.Net.ServicePointManager]::ServerCertificateValidationCallback
    $originalProtocol = [System.Net.ServicePointManager]::SecurityProtocol
    try {
        [System.Net.ServicePointManager]::ServerCertificateValidationCallback = { $true }
        [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12
        $response = Invoke-WebRequest -UseBasicParsing -Method Head -Uri $uri -TimeoutSec 5
        return [PSCustomObject]@{
            Reachable = $true
            Detail = "HTTP $($response.StatusCode)"
        }
    } catch {
        return [PSCustomObject]@{
            Reachable = $false
            Detail = $_.Exception.Message
        }
    } finally {
        [System.Net.ServicePointManager]::ServerCertificateValidationCallback = $originalCallback
        [System.Net.ServicePointManager]::SecurityProtocol = $originalProtocol
    }
}

function Find-SystemInLandscape {
    param(
        [string]$Alias
    )

    foreach ($path in Get-LandscapePaths) {
        if ((Test-Path $path) -and (Get-Item $path).PSIsContainer) {
            $cacheMatch = Get-ChildItem -Path $path -Filter '*.xml' -File -ErrorAction SilentlyContinue |
                ForEach-Object {
                    try {
                        [xml]$xml = Get-Content -Path $_.FullName -Raw -Encoding UTF8
                        $xml.Landscape.Services.Service | Where-Object { $_.systemid -eq $Alias } | Select-Object -First 1
                    } catch {
                        $null
                    }
                } |
                Select-Object -First 1
            if ($cacheMatch) {
                return $cacheMatch
            }
            continue
        }

        if (-not (Test-Path $path)) {
            continue
        }

        try {
            [xml]$xml = Get-Content -Path $path -Raw -Encoding UTF8
            $match = $xml.Landscape.Services.Service | Where-Object { $_.systemid -eq $Alias } | Select-Object -First 1
            if ($match) {
                return $match
            }
            $classic = $xml.Landscape.Systems.System | Where-Object { $_.systemid -eq $Alias -or $_.name -eq $Alias } | Select-Object -First 1
            if ($classic) {
                return $classic
            }
        } catch {
            continue
        }
    }

    return $null
}

function Find-NwbcClient {
    param(
        [string]$Alias
    )

    $recentsDir = Get-NwbcRecentsDir
    if (-not (Test-Path $recentsDir)) {
        return $null
    }

    $match = Get-ChildItem -Path $recentsDir -Filter '*.recents' -File -ErrorAction SilentlyContinue |
        ForEach-Object {
            try {
                [xml]$xml = Get-Content -Path $_.FullName -Raw -Encoding UTF8
                $xml.recents.recent | Where-Object { $_.url -like "*~sysid=$Alias*" -or $_.connection -like "*[$Alias]*" } | Select-Object -First 1
            } catch {
                $null
            }
        } |
        Select-Object -First 1

    if ($match) {
        return $match.client
    }
    return $null
}

$results = New-Object System.Collections.Generic.List[object]

$landscapeFile = Join-Path (Get-WindowsHome) 'AppData\Roaming\SAP\Common\SAPUILandscape.xml'
if (Test-Path $landscapeFile) {
    $results.Add((New-CheckResult -Name 'SAP GUI landscape' -Status 'PASS' -Detail $landscapeFile))
} else {
    $results.Add((New-CheckResult -Name 'SAP GUI landscape' -Status 'FAIL' -Detail 'SAPUILandscape.xml not found' -Action 'Install SAP GUI or ensure the SAP landscape file exists under AppData\Roaming\SAP\Common.'))
}

$systemEntry = Find-SystemInLandscape -Alias $SystemAlias
if ($systemEntry) {
    $results.Add((New-CheckResult -Name "System $SystemAlias" -Status 'PASS' -Detail 'Found in SAP landscape or cache'))
} else {
    $results.Add((New-CheckResult -Name "System $SystemAlias" -Status 'FAIL' -Detail 'System alias not found in SAP landscape' -Action "Open SAP Logon and make sure $SystemAlias exists in the local landscape." ))
}

$nwbcClient = Find-NwbcClient -Alias $SystemAlias
if ($nwbcClient) {
    $results.Add((New-CheckResult -Name "NWBC client for $SystemAlias" -Status 'PASS' -Detail "Client $nwbcClient"))
} else {
    $results.Add((New-CheckResult -Name "NWBC client for $SystemAlias" -Status 'WARN' -Detail 'Client not found in NWBC recents' -Action 'Open the target system once in SAP Business Client if you want the client to be auto-filled.'))
}

$jcoJar = Get-JcoJar
if ($jcoJar) {
    $results.Add((New-CheckResult -Name 'SAP JCo jar' -Status 'PASS' -Detail $jcoJar.FullName))
} else {
    $results.Add((New-CheckResult -Name 'SAP JCo jar' -Status 'FAIL' -Detail 'No com.sap.conn.jco_*.jar found' -Action 'Install SAP JCo or the Eclipse plugin that provides com.sap.conn.jco.'))
}

$windowsHome = Get-WindowsHome
$nativeDll = Get-FirstFile -Roots @(
    (Join-Path $windowsHome 'Documents')
    (Join-Path $windowsHome 'AppData\Local')
    (Join-Path $windowsHome '.p2')
) -Patterns @('sapjco3.dll')
if ($nativeDll) {
    $results.Add((New-CheckResult -Name 'SAP JCo native library' -Status 'PASS' -Detail $nativeDll.FullName))
} else {
    $results.Add((New-CheckResult -Name 'SAP JCo native library' -Status 'FAIL' -Detail 'sapjco3.dll not found' -Action 'Place sapjco3.dll in a local directory and point runtime.jco_native_dir to it.'))
}

$secureLoginInstallDir = Get-SecureLoginInstallDir
$sapcrypto = Get-FirstFile -Roots @($secureLoginInstallDir) -Patterns @('sapcrypto.dll') -Depth 4
if ($sapcrypto) {
    $results.Add((New-CheckResult -Name 'sapcrypto' -Status 'PASS' -Detail $sapcrypto.FullName))
} else {
    $results.Add((New-CheckResult -Name 'sapcrypto' -Status 'FAIL' -Detail 'sapcrypto.dll not found' -Action 'Install SAP Secure Login Client.'))
}

if ($secureLoginInstallDir) {
    $results.Add((New-CheckResult -Name 'Secure Login install' -Status 'PASS' -Detail $secureLoginInstallDir))
} else {
    $results.Add((New-CheckResult -Name 'Secure Login install' -Status 'FAIL' -Detail 'Secure Login installation directory not found' -Action 'Install SAP Secure Login Client.'))
}

$hub = Test-SecureLoginHub
if ($hub.Reachable) {
    $results.Add((New-CheckResult -Name 'Secure Login hub' -Status 'PASS' -Detail $hub.Detail))
} else {
    $action = 'Optional: start SAP Secure Login Client / Local Security Hub if you need the local web adapter flow.'
    if (-not $secureLoginInstallDir) {
        $action = 'Install SAP Secure Login Client and start the local security hub.'
    }
    $status = if ($secureLoginInstallDir) { 'WARN' } else { 'FAIL' }
    $detail = if ($secureLoginInstallDir) {
        "$($hub.Detail) (optional for JCo/SNC fetch and proxy)"
    } else {
        $hub.Detail
    }
    $results.Add((New-CheckResult -Name 'Secure Login hub' -Status $status -Detail $detail -Action $action))
}

$runtimeReady = $jcoJar -and $nativeDll -and $sapcrypto
if ($runtimeReady) {
    $results.Add((New-CheckResult -Name 'OpenADT runtime autodetect' -Status 'PASS' -Detail 'Runtime prerequisites can be auto-filled by setup.'))
} else {
    $results.Add((New-CheckResult -Name 'OpenADT runtime autodetect' -Status 'WARN' -Detail 'Runtime detection will be incomplete until the missing items above are fixed.'))
}

$results | Format-Table -AutoSize

$nextActions = $results | Where-Object { $_.Action } | Select-Object -ExpandProperty Action -Unique
if ($nextActions) {
    Write-Host ''
    Write-Host 'Next actions:'
    $nextActions | ForEach-Object { Write-Host " - $_" }
}

$hasFailure = $results.Status -contains 'FAIL'
if ($hasFailure) {
    exit 1
}
exit 0
