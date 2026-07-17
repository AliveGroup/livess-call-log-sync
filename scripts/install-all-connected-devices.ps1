param(
    [string]$ApkPath = (Join-Path (Resolve-Path (Join-Path $PSScriptRoot "..")).Path "app/build/outputs/apk/release/app-release.apk"),
    [string]$PackageName = "br.com.livess.callsync",
    [switch]$NoUninstall,
    [switch]$StopOnError
)

$ErrorActionPreference = "Stop"

if (!(Test-Path -LiteralPath $ApkPath)) {
    throw "APK not found at: $ApkPath"
}

function Resolve-AdbPath {
    $candidatePaths = @()

    if ($env:ANDROID_HOME) {
        $candidatePaths += Join-Path $env:ANDROID_HOME "platform-tools\\adb.exe"
    }
    if ($env:ANDROID_SDK_ROOT) {
        $candidatePaths += Join-Path $env:ANDROID_SDK_ROOT "platform-tools\\adb.exe"
    }

    foreach ($path in $candidatePaths) {
        if (Test-Path -LiteralPath $path) {
            return $path
        }
    }

    $command = Get-Command adb -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Path
    }

    throw "adb not found. Add platform-tools to PATH or define ANDROID_HOME/ANDROID_SDK_ROOT with platform-tools\\adb.exe."
}

$adb = Resolve-AdbPath
$adbOut = & $adb devices
$deviceLines = $adbOut -split "`r?`n" | ForEach-Object { $_.Trim() } |
    Where-Object { $_ -and -not $_.StartsWith("List of devices attached") }

$devices = @()
foreach ($line in $deviceLines) {
    if ($line -match "^(?<serial>\S+)\s+device$") {
        $devices += $Matches["serial"]
    }
}

if ($devices.Count -eq 0) {
    throw "No authorized devices found. Connect phones with USB debug on and confirm permission on each."
}

Write-Host "Found $($devices.Count) device(s): $($devices -join ', ')"
Write-Host "APK: $ApkPath"

foreach ($serial in $devices) {
    Write-Host "Installing on $serial ..."
    try {
        $installArgs = @("install", "-r", "-d", $ApkPath)
        & $adb -s $serial @installArgs | Out-Null

        if ($LASTEXITCODE -ne 0) {
            if (-not $NoUninstall) {
                Write-Host "[WARN] Incremental install failed on $serial; trying clean reinstall..."
                & $adb -s $serial uninstall $PackageName | Out-Null
                if ($LASTEXITCODE -ne 0) {
                    throw "Uninstall command returned error code $LASTEXITCODE"
                }
                & $adb -s $serial install $ApkPath | Out-Null
                if ($LASTEXITCODE -ne 0) {
                    throw "Install command returned error code $LASTEXITCODE"
                }
            } else {
                throw "Install command returned error code $LASTEXITCODE"
            }
        }

        Write-Host "[OK] Installed on $serial"
    }
    catch {
        Write-Host "[ERR] Failed on ${serial}: $($_.Exception.Message)"
        if ($StopOnError) { throw }
        continue
    }
}

Write-Host "Done."
