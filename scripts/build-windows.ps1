$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

if (-not $env:ANDROID_HOME) {
    $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
}

if (Test-Path "$env:ANDROID_HOME") {
    $sdk = $env:ANDROID_HOME -replace '\\','/'
    "sdk.dir=$sdk" | Set-Content "$Root\local.properties" -Encoding ASCII
}

if (Get-Command python -ErrorAction SilentlyContinue) {
    python "$Root\verify-project.py"
}

if (Test-Path "$Root\gradlew.bat") {
    & "$Root\gradlew.bat" --no-daemon testDebugUnitTest assembleDebug
} elseif (Get-Command gradle -ErrorAction SilentlyContinue) {
    gradle --no-daemon testDebugUnitTest assembleDebug
} else {
    throw "Neither gradlew.bat nor Gradle 8.9 was found."
}

$ApkPath = "$Root\app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $ApkPath)) {
    throw "Build completed but APK was not found at $ApkPath"
}

$ApkItem = Get-Item $ApkPath
$ApkHash = (Get-FileHash $ApkPath -Algorithm SHA256).Hash
Write-Host "BUILD VERIFIED"
Write-Host "APK: $ApkPath"
Write-Host "SIZE: $($ApkItem.Length) bytes"
Write-Host "SHA256: $ApkHash"
