# Publishes the password-reset page and the App Link file to Firebase Hosting
# (https://usjm-smart-library.web.app). Run it by double-clicking deploy.bat.
#
# What it does:
#   1. Reads the SHA-256 fingerprint of the key Android Studio signs the app with
#      and writes it into public/.well-known/assetlinks.json (proves the app owns the link).
#   2. Installs the Firebase CLI if it is missing (needs Node.js) and signs you in once.
#   3. Deploys the hosting folder.
#   4. If a phone is plugged in with USB debugging, asks Android to re-check the App Link.

# Native tools (keytool, npm, firebase) write notes to stderr; check $LASTEXITCODE instead of stopping on them.
$ErrorActionPreference = "Continue"
$Project = "usjm-smart-library"
$Package = "ug.ac.usjm.smartlibrary"
$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Here

function Step($text) { Write-Host ""; Write-Host "== $text" -ForegroundColor Cyan }
function Fail($text) { Write-Host ""; Write-Host "STOPPED: $text" -ForegroundColor Red; exit 1 }

# ------------------------------------------------------------------ 1. signing fingerprint
Step "Reading the app's signing fingerprint"
$keytool = @(
    "$env:ProgramFiles\Android\Android Studio\jbr\bin\keytool.exe",
    "$env:LOCALAPPDATA\Programs\Android Studio\jbr\bin\keytool.exe",
    "$env:JAVA_HOME\bin\keytool.exe"
) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
if (-not $keytool) {
    $cmd = Get-Command keytool -ErrorAction SilentlyContinue
    if ($cmd) { $keytool = $cmd.Source }
}
if (-not $keytool) { Fail "keytool was not found. It comes with Android Studio (jbr\bin). Is Android Studio installed in the default folder?" }

$keystore = "$env:USERPROFILE\.android\debug.keystore"
if (-not (Test-Path $keystore)) { Fail "No debug keystore at $keystore. Run the app once from Android Studio, then try again." }

# The debug keystore always uses the public default password "android".
$listing = & $keytool -list -v -keystore $keystore -alias androiddebugkey -storepass android -keypass android 2>&1 | Out-String
$fingerprints = @()
foreach ($m in [regex]::Matches($listing, "SHA256:\s*([0-9A-F:]{95})")) { $fingerprints += $m.Groups[1].Value }

# Optional: a release (upload) key fingerprint for the signed APK later, one per line in release-sha256.txt
if (Test-Path "$Here\release-sha256.txt") {
    foreach ($line in Get-Content "$Here\release-sha256.txt") {
        $f = $line.Trim().ToUpper()
        if ($f -match "^[0-9A-F]{2}(:[0-9A-F]{2}){31}$") { $fingerprints += $f }
    }
}
$fingerprints = $fingerprints | Select-Object -Unique
if ($fingerprints.Count -eq 0) { Fail "Could not read a SHA-256 fingerprint from the debug keystore." }
$fingerprints | ForEach-Object { Write-Host "   $_" }

$json = @"
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "$Package",
      "sha256_cert_fingerprints": [$(($fingerprints | ForEach-Object { "`"$_`"" }) -join ", ")]
    }
  }
]
"@
New-Item -ItemType Directory -Force -Path "$Here\public\.well-known" | Out-Null
# UTF-8 without a byte-order mark, otherwise Android cannot read the file
[System.IO.File]::WriteAllText("$Here\public\.well-known\assetlinks.json", $json, (New-Object System.Text.UTF8Encoding $false))
Write-Host "   Saved public\.well-known\assetlinks.json"

# ------------------------------------------------------------------ 2. Firebase CLI
Step "Checking the Firebase CLI"
if (-not (Get-Command firebase -ErrorAction SilentlyContinue)) {
    if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
        Fail "Node.js is not installed. Install the LTS version from https://nodejs.org (keep the default options), then double-click deploy.bat again."
    }
    Write-Host "   Installing firebase-tools (one time, about a minute)..."
    npm install -g firebase-tools
    if ($LASTEXITCODE -ne 0) { Fail "npm could not install firebase-tools." }
    $env:Path = "$env:APPDATA\npm;$env:Path"
}

& firebase projects:list --json *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "   A browser window will open. Sign in with the Google account that owns the Firebase project."
    & firebase login
    if ($LASTEXITCODE -ne 0) { Fail "Firebase sign-in did not finish." }
}

# ------------------------------------------------------------------ 3. deploy
Step "Deploying to https://$Project.web.app"
& firebase deploy --only hosting --project $Project
if ($LASTEXITCODE -ne 0) { Fail "The deploy failed. Read the red message above." }

try {
    $live = Invoke-WebRequest -UseBasicParsing "https://$Project.web.app/.well-known/assetlinks.json"
    if ($live.Content -match $fingerprints[0]) { Write-Host "   assetlinks.json is live and correct." -ForegroundColor Green }
} catch { Write-Host "   (Could not re-download assetlinks.json to check it; that is usually fine.)" }

# ------------------------------------------------------------------ 4. phone (optional)
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (Test-Path $adb) {
    $devices = (& $adb devices) | Select-String "\tdevice$"
    if ($devices) {
        Step "Asking the connected phone to re-check the link"
        & $adb shell pm verify-app-links --re-verify $Package 2>$null
        Start-Sleep -Seconds 5
        & $adb shell pm get-app-links $Package
        Write-Host "   'verified' next to both hosts means reset links will open the app."
    }
}

Write-Host ""
Write-Host "DONE." -ForegroundColor Green
Write-Host "Last step (once): Firebase console > Authentication > Templates > Password reset > pencil icon >"
Write-Host "  'Customize action URL' and enter:  https://$Project.web.app/auth/action"
