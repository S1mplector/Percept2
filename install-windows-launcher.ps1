# -----------------------------------------------------------------------------
#  install-windows-launcher.ps1
#
#  Installs a "JVN Engine Hub" shortcut for the current Windows user:
#
#    Start Menu\Programs\JVN Engine Hub.lnk
#    Desktop\JVN Engine Hub.lnk
#
#  It also writes hidden launcher wrappers under %LOCALAPPDATA%\JVN Engine Hub.
#  The shortcuts do not open a command prompt; failures are logged and shown in
#  a native message box.
#
#  Uninstall:
#    Remove the shortcuts and %LOCALAPPDATA%\JVN Engine Hub
# -----------------------------------------------------------------------------

[CmdletBinding()]
param(
  [switch]$NoDesktop
)

$ErrorActionPreference = "Stop"

function Fail([string]$Message) {
  Write-Error "[installer] $Message"
  exit 1
}

function PsQuote([string]$Value) {
  return "'" + $Value.Replace("'", "''") + "'"
}

function Copy-SvgIcon([string]$Root, [string]$Path) {
  $source = Join-Path $Root "docs\assets\images\jvn_logo.svg"
  if (-not (Test-Path -LiteralPath $source)) {
    Fail "JVN SVG logo was not found at $source"
  }
  Copy-Item -LiteralPath $source -Destination $Path -Force
}

function New-Shortcut([string]$ShortcutPath, [string]$TargetPath, [string]$Arguments, [string]$WorkingDirectory, [string]$Description, [string]$IconPath) {
  $shell = New-Object -ComObject WScript.Shell
  $shortcut = $shell.CreateShortcut($ShortcutPath)
  $shortcut.TargetPath = $TargetPath
  $shortcut.Arguments = $Arguments
  $shortcut.WorkingDirectory = $WorkingDirectory
  $shortcut.Description = $Description
  if ($IconPath) {
    $shortcut.IconLocation = $IconPath
  }
  $shortcut.Save()
}

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$JvnBat = Join-Path $ProjectRoot "jvn.bat"
$GradleBat = Join-Path $ProjectRoot "gradlew.bat"

if (-not (Test-Path -LiteralPath $JvnBat)) { Fail "jvn.bat was not found at $JvnBat" }
if (-not (Test-Path -LiteralPath $GradleBat)) { Fail "gradlew.bat was not found at $GradleBat" }

$InstallDir = Join-Path $env:LOCALAPPDATA "JVN Engine Hub"
$LogDir = Join-Path $env:LOCALAPPDATA "JVN Engine Hub\Logs"
$LauncherPs1 = Join-Path $InstallDir "jvn-engine-hub-launcher.ps1"
$LauncherVbs = Join-Path $InstallDir "jvn-engine-hub-launcher.vbs"
$IconSvg = Join-Path $InstallDir "jvn-engine-hub.svg"
$LogFile = Join-Path $LogDir "launcher.log"

New-Item -ItemType Directory -Force -Path $InstallDir, $LogDir | Out-Null

Copy-SvgIcon $ProjectRoot $IconSvg

$projectRootLiteral = PsQuote $ProjectRoot
$logFileLiteral = PsQuote $LogFile

$launcherPs1Content = @"
`$ErrorActionPreference = "Stop"
`$ProjectRoot = $projectRootLiteral
`$LogFile = $logFileLiteral
`$GradleBat = Join-Path `$ProjectRoot "gradlew.bat"

function Write-LaunchLog([string]`$Message) {
  Add-Content -LiteralPath `$LogFile -Value `$Message
}

function Show-Failure([string]`$Message) {
  try {
    Add-Type -AssemblyName System.Windows.Forms
    [System.Windows.Forms.MessageBox]::Show("`$Message`r`n`r`nLog: `$LogFile", "JVN Engine Hub failed", [System.Windows.Forms.MessageBoxButtons]::OK, [System.Windows.Forms.MessageBoxIcon]::Error) | Out-Null
  } catch {
  }
}

New-Item -ItemType Directory -Force -Path (Split-Path -Parent `$LogFile) | Out-Null
Write-LaunchLog "---- `$((Get-Date).ToString('yyyy-MM-ddTHH:mm:sszzz')) ----"
Write-LaunchLog "[JVN] Project: `$ProjectRoot"
Write-LaunchLog "[JVN] Starting Engine Hub..."

if (-not (Test-Path -LiteralPath `$GradleBat)) {
  Write-LaunchLog "[JVN] Missing gradlew.bat in `$ProjectRoot"
  Show-Failure "Missing gradlew.bat in the project directory."
  exit 1
}

try {
  `$pushedLocation = `$false
  Push-Location -LiteralPath `$ProjectRoot
  `$pushedLocation = `$true
  & `$GradleBat -q --console=plain -p `$ProjectRoot ":hub:run" >> `$LogFile 2>&1
  `$status = `$LASTEXITCODE
  if (`$null -eq `$status) { `$status = 0 }
} catch {
  Write-LaunchLog "[JVN] Startup exception: `$(`$_.Exception.Message)"
  Show-Failure "Startup failed before the hub could run."
  exit 1
} finally {
  if (`$pushedLocation) { Pop-Location }
}

if (`$status -ne 0) {
  Write-LaunchLog "[JVN] Launcher failed with exit code `$status."
  Show-Failure "Startup failed with exit code `$status."
}
exit `$status
"@

$launcherVbsContent = @"
Set shell = CreateObject("WScript.Shell")
shell.Run "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""$LauncherPs1""", 0, False
"@

Set-Content -LiteralPath $LauncherPs1 -Value $launcherPs1Content -Encoding UTF8
Set-Content -LiteralPath $LauncherVbs -Value $launcherVbsContent -Encoding ASCII

$StartMenuDir = Join-Path ([Environment]::GetFolderPath("StartMenu")) "Programs"
$StartShortcut = Join-Path $StartMenuDir "JVN Engine Hub.lnk"
New-Item -ItemType Directory -Force -Path $StartMenuDir | Out-Null
$Wscript = Join-Path $env:WINDIR "System32\wscript.exe"
New-Shortcut $StartShortcut $Wscript "`"$LauncherVbs`"" $ProjectRoot "Launch Java Vector Nexus Engine Hub" $IconSvg

if (-not $NoDesktop) {
  $DesktopDir = [Environment]::GetFolderPath("Desktop")
  if ($DesktopDir) {
    $DesktopShortcut = Join-Path $DesktopDir "JVN Engine Hub.lnk"
    New-Shortcut $DesktopShortcut $Wscript "`"$LauncherVbs`"" $ProjectRoot "Launch Java Vector Nexus Engine Hub" $IconSvg
  }
}

Write-Host "[installer] installed Start Menu shortcut: $StartShortcut"
if (-not $NoDesktop -and $DesktopShortcut) {
  Write-Host "[installer] installed Desktop shortcut: $DesktopShortcut"
}
Write-Host "[installer] installed hidden PowerShell launcher: $LauncherPs1"
Write-Host "[installer] installed hidden WSH launcher: $LauncherVbs"
Write-Host "[installer] installed SVG icon: $IconSvg"
Write-Host "[installer] launch log: $LogFile"
