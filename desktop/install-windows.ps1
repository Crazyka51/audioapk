<#
.SYNOPSIS
Installs the Windows desktop build of AudioDetective Pro and creates a desktop shortcut.

.DESCRIPTION
Copies an already built Compose Desktop distribution to a stable per-user location
(%LOCALAPPDATA%\Programs\AudioDetectivePro), generates the application icon from the Android
launcher artwork and creates a shortcut on the desktop pointing at the bundled .exe.

This is the same what `gradle :desktop:createDistributable` produces, but installed somewhere
the build folder cleanup cannot remove.

.PARAMETER Source
Path to the built distribution. Defaults to the Compose output of the desktop module.

.PARAMETER NoShortcut
Installs the files but does not create the desktop shortcut.

.PARAMETER Uninstall
Removes the installed files and the desktop shortcut.

.EXAMPLE
gradle :desktop:createDistributable
.\desktop\install-windows.ps1

.EXAMPLE
.\desktop\install-windows.ps1 -Uninstall
#>
[CmdletBinding()]
param(
    [string]$Source = "",
    [switch]$NoShortcut,
    [switch]$Uninstall
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($Source)) {
    $Source = Join-Path $repoRoot "desktop\build\compose\binaries\main\app\AudioDetectivePro"
}

$installDir = Join-Path $env:LOCALAPPDATA "Programs\AudioDetectivePro"
$exeName = "AudioDetectivePro.exe"
$iconName = "AudioDetectivePro.ico"
$shortcutName = "AudioDetective Pro.lnk"
$desktop = [Environment]::GetFolderPath("Desktop")
$shortcutPath = Join-Path $desktop $shortcutName

function Remove-DesktopShortcut {
    if (Test-Path $shortcutPath) {
        Remove-Item $shortcutPath -Force
        Write-Host "Removed shortcut: $shortcutPath"
    }
}

if ($Uninstall) {
    Remove-DesktopShortcut
    if (Test-Path $installDir) {
        Remove-Item $installDir -Recurse -Force
        Write-Host "Removed installation: $installDir"
    } else {
        Write-Host "Nothing installed at $installDir"
    }
    return
}

$sourceExe = Join-Path $Source $exeName
if (-not (Test-Path $sourceExe)) {
    throw "Distribution not found at '$Source'. Run 'gradle :desktop:createDistributable' first."
}

Write-Host "Installing from: $Source"
Remove-Item $installDir -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $installDir | Out-Null
Copy-Item (Join-Path $Source "*") $installDir -Recurse -Force

# The launcher needs a .ico for the shortcut. The Android launcher artwork is the closest match to
# the app identity, so it is converted on the fly instead of committing a binary icon.
$iconPath = Join-Path $installDir $iconName
$iconSource = Join-Path $repoRoot "app\src\main\res\mipmap-xxxhdpi\ic_launcher.png"
if ((Test-Path $iconSource) -and -not (Test-Path $iconPath)) {
    Add-Type -AssemblyName System.Drawing
    $image = [System.Drawing.Image]::FromFile($iconSource)
    try {
        $bitmap = New-Object System.Drawing.Bitmap $image, 256, 256
        try {
            $handle = $bitmap.GetHicon()
            $icon = [System.Drawing.Icon]::FromHandle($handle)
            $stream = [IO.File]::Create($iconPath)
            try { $icon.Save($stream) } finally { $stream.Close() }
        } finally { $bitmap.Dispose() }
    } finally { $image.Dispose() }
    Write-Host "Generated icon: $iconPath"
}

$installedExe = Join-Path $installDir $exeName
Write-Host "Installed: $installedExe"

if ($NoShortcut) {
    Write-Host "Skipping shortcut creation (-NoShortcut)."
    return
}

Remove-DesktopShortcut
$shell = New-Object -ComObject WScript.Shell
try {
    $shortcut = $shell.CreateShortcut($shortcutPath)
    $shortcut.TargetPath = $installedExe
    $shortcut.WorkingDirectory = $installDir
    if (Test-Path $iconPath) { $shortcut.IconLocation = "$iconPath,0" }
    $shortcut.Description = "AudioDetective Pro - Forensic Audio Analysis"
    $shortcut.Save()
} finally {
    [Runtime.InteropServices.Marshal]::ReleaseComObject($shell) | Out-Null
}

Write-Host "Shortcut created: $shortcutPath"
Write-Host "The recording library lives in $env:USERPROFILE\Music\AudioDetective"
