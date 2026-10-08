$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

if (-not $env:ANDROID_HOME) {
    $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
}

$sdk = $env:ANDROID_HOME -replace '\\','/'
"sdk.dir=$sdk" | Set-Content "$Root\local.properties" -Encoding ASCII

if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) {
    throw "Gradle 8.9 must be in PATH"
}

gradle --no-daemon clean assembleDebug

Write-Host "APK: $Root\app\build\outputs\apk\debug\app-debug.apk"
