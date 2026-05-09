$ErrorActionPreference = "Stop"

$RootDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $RootDir

.\gradlew.bat :hub:packageEngineHubJar

Write-Host ""
Write-Host "Packaged Engine Hub jar:"
Get-ChildItem -Path (Join-Path $RootDir "build\distributions") -Filter "jvn-engine-hub-*.jar" |
  ForEach-Object { Write-Host "  $($_.FullName)" }
