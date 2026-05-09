$ErrorActionPreference = "Stop"

$RootDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $RootDir

$Task = ":hub:packageEngineHubJar"
$Pattern = "jvn-engine-hub-*.jar"
$ExcludeCached = $true
if ($args.Count -gt 0 -and ($args[0] -eq "--with-cache" -or $args[0] -eq "--cached")) {
  $Task = ":hub:packageEngineHubJarWithCache"
  $Pattern = "jvn-engine-hub-cached-*.jar"
  $ExcludeCached = $false
}

.\gradlew.bat $Task

Write-Host ""
Write-Host "Packaged Engine Hub jar:"
Get-ChildItem -Path (Join-Path $RootDir "build\distributions") -Filter $Pattern |
  Where-Object { -not $ExcludeCached -or $_.Name -notlike "jvn-engine-hub-cached-*" } |
  ForEach-Object { Write-Host "  $($_.FullName)" }
