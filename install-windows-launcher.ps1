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

function XmlEscape([string]$Value) {
  return [System.Security.SecurityElement]::Escape($Value)
}

function PsQuote([string]$Value) {
  return "'" + $Value.Replace("'", "''") + "'"
}

function Read-ProjectVersion([string]$Root) {
  $gradleProps = Join-Path $Root "gradle.properties"
  if (Test-Path -LiteralPath $gradleProps) {
    $line = Get-Content -LiteralPath $gradleProps | Where-Object { $_ -match '^\s*jvnVersion\s*=' } | Select-Object -First 1
    if ($line -and $line -match '^\s*jvnVersion\s*=\s*(\S+)') {
      return $Matches[1]
    }
  }

  $buildFile = Join-Path $Root "build.gradle.kts"
  if (Test-Path -LiteralPath $buildFile) {
    $line = Get-Content -LiteralPath $buildFile | Where-Object { $_ -match 'val\s+jvnVersion' } | Select-Object -First 1
    if ($line -and $line -match '\?:\s*"([^"]+)"') {
      return $Matches[1]
    }
  }
  return "dev"
}

function Write-SvgIcon([string]$Path, [string]$Version) {
  $label = $Version
  if (-not $label.StartsWith("v")) { $label = "v$label" }
  $label = XmlEscape $label
  $svg = @"
<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" role="img" aria-label="JVN $label">
  <defs>
    <linearGradient id="bg" x1="24" y1="24" x2="232" y2="232" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#121826"/>
      <stop offset="0.55" stop-color="#1e2d4c"/>
      <stop offset="1" stop-color="#0b111f"/>
    </linearGradient>
    <linearGradient id="mark" x1="56" y1="42" x2="204" y2="206" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#ffb35c"/>
      <stop offset="0.48" stop-color="#ff6f3c"/>
      <stop offset="1" stop-color="#4fb7ff"/>
    </linearGradient>
  </defs>
  <rect x="18" y="18" width="220" height="220" rx="44" fill="url(#bg)"/>
  <text x="128" y="137" text-anchor="middle" font-family="Arial, sans-serif" font-size="70" font-weight="900" fill="url(#mark)" opacity="0.96">JVN</text>
  <rect x="54" y="182" width="148" height="32" rx="16" fill="#07101f" opacity="0.74"/>
  <text x="128" y="204" text-anchor="middle" font-family="Arial, sans-serif" font-size="18" font-weight="700" fill="#ffcf91">$label</text>
</svg>
"@
  Set-Content -LiteralPath $Path -Value $svg -Encoding UTF8
}

function New-Shortcut([string]$ShortcutPath, [string]$TargetPath, [string]$Arguments, [string]$WorkingDirectory, [string]$Description) {
  $shell = New-Object -ComObject WScript.Shell
  $shortcut = $shell.CreateShortcut($ShortcutPath)
  $shortcut.TargetPath = $TargetPath
  $shortcut.Arguments = $Arguments
  $shortcut.WorkingDirectory = $WorkingDirectory
  $shortcut.Description = $Description
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

$Version = Read-ProjectVersion $ProjectRoot
Write-SvgIcon $IconSvg $Version

$projectRootLiteral = PsQuote $ProjectRoot
$logFileLiteral = PsQuote $LogFile

$launcherPs1Content = @"
`$ErrorActionPreference = "Stop"
`$ProjectRoot = $projectRootLiteral
`$LogFile = $logFileLiteral
`$JvnBat = Join-Path `$ProjectRoot "jvn.bat"

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

if (-not (Test-Path -LiteralPath `$JvnBat)) {
  Write-LaunchLog "[JVN] Missing jvn.bat in `$ProjectRoot"
  Show-Failure "Missing jvn.bat in the project directory."
  exit 1
}

try {
  `$psi = New-Object System.Diagnostics.ProcessStartInfo
  `$psi.FileName = `$env:ComSpec
  if ([string]::IsNullOrWhiteSpace(`$psi.FileName)) { `$psi.FileName = "cmd.exe" }
  `$cmdLine = '"' + `$JvnBat + '" >> "' + `$LogFile + '" 2>&1'
  `$psi.Arguments = '/d /s /c "' + `$cmdLine + '"'
  `$psi.WorkingDirectory = `$ProjectRoot
  `$psi.UseShellExecute = `$false
  `$psi.CreateNoWindow = `$true
  `$psi.WindowStyle = [System.Diagnostics.ProcessWindowStyle]::Hidden
  `$process = [System.Diagnostics.Process]::Start(`$psi)
  `$process.WaitForExit()
  `$status = `$process.ExitCode
} catch {
  Write-LaunchLog "[JVN] Startup exception: `$(`$_.Exception.Message)"
  Show-Failure "Startup failed before the hub could run."
  exit 1
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
New-Shortcut $StartShortcut $Wscript "`"$LauncherVbs`"" $ProjectRoot "Launch Java Vector Nexus Engine Hub"

if (-not $NoDesktop) {
  $DesktopDir = [Environment]::GetFolderPath("Desktop")
  if ($DesktopDir) {
    $DesktopShortcut = Join-Path $DesktopDir "JVN Engine Hub.lnk"
    New-Shortcut $DesktopShortcut $Wscript "`"$LauncherVbs`"" $ProjectRoot "Launch Java Vector Nexus Engine Hub"
  }
}

Write-Host "[installer] installed Start Menu shortcut: $StartShortcut"
if (-not $NoDesktop -and $DesktopShortcut) {
  Write-Host "[installer] installed Desktop shortcut: $DesktopShortcut"
}
Write-Host "[installer] installed hidden PowerShell launcher: $LauncherPs1"
Write-Host "[installer] installed hidden WSH launcher: $LauncherVbs"
Write-Host "[installer] generated SVG icon: $IconSvg"
Write-Host "[installer] launch log: $LogFile"
