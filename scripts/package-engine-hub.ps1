$ErrorActionPreference = "Stop"

$RootDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $RootDir

$Task = ":hub:packageEngineHubRelease"
$Pattern = "jvn-engine-hub-*.jar"
$ExcludeLite = $true
if ($args.Count -gt 0 -and $args[0] -eq "--lite") {
  $Task = ":hub:packageEngineHubLiteJar"
  $Pattern = "jvn-engine-hub-lite-*.jar"
  $ExcludeLite = $false
}

.\gradlew.bat $Task

Write-Host ""
Write-Host "Packaged Engine Hub jar:"
Get-ChildItem -Path (Join-Path $RootDir "build\distributions") -Filter $Pattern |
  Where-Object { -not $ExcludeLite -or $_.Name -notlike "jvn-engine-hub-lite-*" } |
  ForEach-Object { Write-Host "  $($_.FullName)" }
