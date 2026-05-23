<#
.SYNOPSIS
    End-to-end Messenger Z patcher: clean conflicting signature permissions in the
    Messenger APK, rebuild it, re-sign it, then embed the Messenger Z module via LSPatch.

.DESCRIPTION
    Background: Messenger declares signature-level permissions (e.g.
    `com.facebook.permission.prod.FB_APP_COMMUNICATION`) that are also declared by the
    Facebook app. After LSPatch re-signs the APK with its own cert, Android refuses to
    install because two apps now claim the same `<permission>` name under different
    signing certs (INSTALL_FAILED_DUPLICATE_PERMISSION). This script strips those
    conflicting `<permission>` declarations from the Messenger manifest before LSPatch
    sees the APK, fixing the install collision.

    Pipeline:
      1. APKEditor d -t xml <messenger.apk>   -> decoded sources (ARSCLib, no aapt2)
      2. strip selected `<permission>` blocks from AndroidManifest.xml
      3. APKEditor b                          -> rebuilt unsigned APK
      4. uber-apk-signer                      -> re-signed with debug cert
      5. lspatch -m <module.apk> -v <signed.apk>  -> Messenger Z patched APK

    Why APKEditor instead of apktool: Meta's APKs include obfuscated resource folder
    names like `res/invalid18/` that aapt2 rejects on rebuild. APKEditor uses ARSCLib
    internally and avoids aapt2, so the round-trip succeeds.

    Why `-dex` on APKEditor decode: full smali round-trip changes dex byte layout,
    which Meta's native integrity checks detect and crash on launch. `-dex` copies
    dex files raw on decode and re-packs them raw on build, so every dex byte stays
    identical. Only resources/manifest get re-serialized.

.PARAMETER MessengerApk
    Path to the source Messenger APK from APKMirror. Defaults to the only *.apk in repo root.

.PARAMETER ModuleApk
    Path to the Messenger Z module APK built by ./gradlew :app:assembleDebug.
    Defaults to app/build/outputs/apk/debug/app-debug.apk.

.PARAMETER OutDir
    Directory for the final LSPatched APK. Defaults to <repo>/dist.

.PARAMETER Clean
    Wipe intermediate work/ directory before running. Use when the previous run aborted
    mid-flight or you changed which permissions get stripped.

.EXAMPLE
    pwsh tools/repack-messenger.ps1

.EXAMPLE
    pwsh tools/repack-messenger.ps1 -Clean
#>

[CmdletBinding()]
param(
    [string]$MessengerApk,
    [string]$ModuleApk,
    [string]$OutDir,
    [switch]$Clean
)

$ErrorActionPreference = "Stop"

# -- Resolve paths -----------------------------------------------------------
$RepoRoot = Split-Path -Parent $PSScriptRoot
$ToolsDir = $PSScriptRoot
$WorkDir  = Join-Path $ToolsDir "work"

if (-not $MessengerApk) {
    $candidates = Get-ChildItem -Path $RepoRoot -Filter "com.facebook.orca_*.apk" -File
    if ($candidates.Count -eq 0) {
        throw "No Messenger APK found at repo root. Pass -MessengerApk <path>."
    }
    if ($candidates.Count -gt 1) {
        throw "Multiple Messenger APKs at repo root; pass -MessengerApk to disambiguate."
    }
    $MessengerApk = $candidates[0].FullName
}
if (-not $ModuleApk) {
    $ModuleApk = Join-Path $RepoRoot "app\build\outputs\apk\debug\app-debug.apk"
}
if (-not $OutDir) {
    $OutDir = Join-Path $RepoRoot "dist"
}

foreach ($p in @($MessengerApk, $ModuleApk)) {
    if (-not (Test-Path $p)) { throw "Missing input APK: $p" }
}

$ApkEditor = Join-Path $ToolsDir "apkeditor.jar"
$Signer    = Join-Path $ToolsDir "uber-apk-signer.jar"
$LsPatch   = Join-Path $ToolsDir "lspatch\lspatch.jar"
foreach ($p in @($ApkEditor, $Signer, $LsPatch)) {
    if (-not (Test-Path $p)) { throw "Missing tool: $p" }
}

# -- JDK on PATH -------------------------------------------------------------
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $jbr = "C:\Program Files\Android\Android Studio\jbr"
    if (Test-Path "$jbr\bin\java.exe") {
        $env:JAVA_HOME = $jbr
    } else {
        throw "JAVA_HOME not set and Android Studio JBR not at $jbr. Set JAVA_HOME first."
    }
}
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# -- Workspace prep ----------------------------------------------------------
if ($Clean -and (Test-Path $WorkDir)) {
    Write-Host "[clean] Removing $WorkDir"
    Remove-Item -Recurse -Force $WorkDir
}
New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null
New-Item -ItemType Directory -Force -Path $OutDir  | Out-Null

$DecodeDir   = Join-Path $WorkDir "decoded"
$RebuiltApk  = Join-Path $WorkDir "messenger-rebuilt.apk"
$SignedDir   = Join-Path $WorkDir "signed"
$SignedApk   = Join-Path $SignedDir "messenger-rebuilt-aligned-debugSigned.apk"

# -- Conflicting signature permissions to strip ------------------------------
# These are declared by the Messenger APK at signature protection level. The Facebook
# app declares them too under Meta's cert. After LSPatch re-signs Messenger with a
# different cert, Android rejects install with INSTALL_FAILED_DUPLICATE_PERMISSION.
$ConflictingPermissions = @(
    "com.facebook.permission.prod.FB_APP_COMMUNICATION",
    "com.facebook.receiver.permission.ACCESS"
)

# -- 1. Decode ---------------------------------------------------------------
if (Test-Path $DecodeDir) {
    Write-Host "[1/5] Decoded dir already exists, skipping decode. Pass -Clean to redo."
} else {
    Write-Host "[1/5] APKEditor d -t xml -dex -i $MessengerApk -o $DecodeDir"
    & java -jar $ApkEditor d -t xml -dex -i $MessengerApk -o $DecodeDir -f
    if ($LASTEXITCODE -ne 0) { throw "APKEditor decode failed (exit $LASTEXITCODE)" }
}

# -- 2. Strip conflicting permissions ----------------------------------------
$ManifestPath = Join-Path $DecodeDir "AndroidManifest.xml"
if (-not (Test-Path $ManifestPath)) { throw "AndroidManifest.xml missing after decode: $ManifestPath" }

Write-Host "[2/5] Stripping conflicting <permission> declarations from manifest"
$manifest = Get-Content $ManifestPath -Raw
$originalLength = $manifest.Length
$stripped = 0
foreach ($permName in $ConflictingPermissions) {
    # Match the full <permission ... android:name="<permName>" ... /> block,
    # whether self-closed or with explicit </permission>. android:name can appear
    # in any attribute position so anchor on the literal name.
    $escaped = [regex]::Escape($permName)
    $pattern = "(?s)\s*<permission\b(?:[^>]*?\s)?android:name=`"$escaped`"(?:\s[^>]*?)?(?:/>|>\s*</permission>)"
    $rx = [regex]::new($pattern)
    $matchCount = $rx.Matches($manifest).Count
    if ($matchCount -gt 0) {
        $manifest = $rx.Replace($manifest, "")
        Write-Host "       removed $matchCount block(s) for $permName"
        $stripped += $matchCount
    } else {
        Write-Warning "       no match for $permName (already absent or manifest format changed)"
    }
}
if ($stripped -eq 0) {
    Write-Warning "No permissions stripped. Manifest may already be clean, or names drifted."
}
Set-Content -Path $ManifestPath -Value $manifest -NoNewline -Encoding UTF8
Write-Host "       manifest: $originalLength -> $($manifest.Length) chars"

# -- 3. Rebuild --------------------------------------------------------------
if (Test-Path $RebuiltApk) { Remove-Item -Force $RebuiltApk }
Write-Host "[3/5] APKEditor b -i $DecodeDir -o $RebuiltApk"
& java -jar $ApkEditor b -i $DecodeDir -o $RebuiltApk
if ($LASTEXITCODE -ne 0) { throw "APKEditor build failed (exit $LASTEXITCODE)" }

# -- 4. Re-sign with debug cert ----------------------------------------------
if (Test-Path $SignedDir) { Remove-Item -Recurse -Force $SignedDir }
New-Item -ItemType Directory -Force -Path $SignedDir | Out-Null
Write-Host "[4/5] uber-apk-signer sign $RebuiltApk -> $SignedDir"
& java -jar $Signer --apks $RebuiltApk --out $SignedDir
if ($LASTEXITCODE -ne 0) { throw "uber-apk-signer failed (exit $LASTEXITCODE)" }
if (-not (Test-Path $SignedApk)) {
    $found = Get-ChildItem $SignedDir -Filter "*.apk" -Recurse | Select-Object -First 1
    if (-not $found) { throw "No signed APK produced in $SignedDir" }
    $SignedApk = $found.FullName
}
Write-Host "       signed: $SignedApk"

# -- 5. LSPatch --------------------------------------------------------------
Write-Host "[5/5] lspatch -m $ModuleApk -v $SignedApk -o $OutDir"
& java -jar $LsPatch -m $ModuleApk -f -l 2 -o $OutDir -v $SignedApk
if ($LASTEXITCODE -ne 0) { throw "lspatch failed (exit $LASTEXITCODE)" }

$final = Get-ChildItem $OutDir -Filter "*lspatched.apk" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
Write-Host ""
Write-Host "DONE. Patched APK:"
Write-Host "  $($final.FullName)"
Write-Host ""
Write-Host "Install on device:"
Write-Host "  - Uninstall existing Messenger / Messenger Z first"
Write-Host "  - Transfer the APK above to the phone and install"
