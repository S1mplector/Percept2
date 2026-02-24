param(
  [ValidateSet("Release", "Debug")]
  [string]$BuildType = "Release",
  [switch]$Clean,
  [switch]$WithTests,
  [switch]$WithoutJni
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BuildDir = Join-Path $ScriptDir "build"
$BuildTests = if ($WithTests) { "ON" } else { "OFF" }
$BuildJni = if ($WithoutJni) { "OFF" } else { "ON" }

if ($Clean -and (Test-Path $BuildDir)) {
  Remove-Item -Recurse -Force $BuildDir
}

if ($BuildJni -eq "ON" -and -not $env:JAVA_HOME) {
  $javac = Get-Command javac -ErrorAction SilentlyContinue
  if ($javac) {
    $javacDir = Split-Path -Parent $javac.Source
    $env:JAVA_HOME = Split-Path -Parent $javacDir
  }
}

cmake -S $ScriptDir -B $BuildDir `
  -DCMAKE_BUILD_TYPE=$BuildType `
  -DSIMJOT_NATIVE_BUILD_TESTS=$BuildTests `
  -DJVN_BUILD_JNI_BRIDGE=$BuildJni `
  -DJAVA_HOME="$env:JAVA_HOME"

cmake --build $BuildDir --config $BuildType --parallel

Write-Host ""
Write-Host "Build complete."
Write-Host "Expected outputs:"
Write-Host "  $BuildDir\\$BuildType\\simjot_native.dll (or $BuildDir\\simjot_native.dll)"
Write-Host "  $BuildDir\\$BuildType\\jvn_native_bridge.dll (if JNI enabled)"
