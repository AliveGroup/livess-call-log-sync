param(
    [string]$ProjectDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [ValidateSet("release", "pilot")]
    [string]$Variant = "release"
)

$ErrorActionPreference = "Stop"

$required = @(
    "LIVESS_ANDROID_KEYSTORE_B64",
    "LIVESS_ANDROID_KEYSTORE_PASSWORD",
    "LIVESS_ANDROID_KEY_ALIAS",
    "LIVESS_ANDROID_KEY_PASSWORD"
)

foreach ($name in $required) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "Missing required release signing environment variable: $name"
    }
}

$temporaryKeystore = Join-Path $ProjectDir "keys\livess-sync-release-materialized.jks"
$keystoreDirectory = Split-Path -Parent $temporaryKeystore

if (-not (Test-Path -LiteralPath $keystoreDirectory)) {
    New-Item -ItemType Directory -Path $keystoreDirectory | Out-Null
}

try {
    [IO.File]::WriteAllBytes(
        $temporaryKeystore,
        [Convert]::FromBase64String($env:LIVESS_ANDROID_KEYSTORE_B64)
    )

    $env:LIVESS_ANDROID_KEYSTORE_FILE = $temporaryKeystore

    $variantTitle = (Get-Culture).TextInfo.ToTitleCase($Variant)

    & (Join-Path $ProjectDir "gradlew.bat") `
        -p $ProjectDir `
        --no-daemon `
        testDebugUnitTest `
        lintDebug `
        lintRelease `
        verifyReleaseSigning `
        "assemble$variantTitle"

    if ($LASTEXITCODE -ne 0) {
        throw "Official release build failed."
    }
}
finally {
    Remove-Item Env:LIVESS_ANDROID_KEYSTORE_FILE -ErrorAction SilentlyContinue
    if (Test-Path -LiteralPath $temporaryKeystore) {
        Remove-Item -LiteralPath $temporaryKeystore -Force
    }
}
