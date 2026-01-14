Param(
  [string]$JavaHome = $env:JAVA_HOME,
  [string]$OutDir = "$PSScriptRoot"
)

if ([string]::IsNullOrWhiteSpace($JavaHome)) {
  $javac = Get-Command javac -ErrorAction SilentlyContinue
  if ($javac) {
    $JavaHome = Split-Path -Parent (Split-Path -Parent $javac.Path)
  }
}

if ([string]::IsNullOrWhiteSpace($JavaHome)) {
  Write-Error "JAVA_HOME is not set and javac not found."
  exit 1
}

$outPath = Join-Path $OutDir "jvn_math.dll"
$include1 = Join-Path $JavaHome "include"
$include2 = Join-Path $JavaHome "include\win32"

cl /EHsc /LD "$PSScriptRoot\jvn_math.cpp" `
  /I "$include1" /I "$include2" `
  /Fe:"$outPath"

Write-Host "Built $outPath"
