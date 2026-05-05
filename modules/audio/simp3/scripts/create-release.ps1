# SiMP3 Release Creation Script
# This script builds and packages SiMP3 for distribution

param(
    [string]$Version = "1.0.0",
    [switch]$SkipTests = $false
)

Write-Host "=== SiMP3 Release Builder ===" -ForegroundColor Cyan
Write-Host "Version: $Version" -ForegroundColor Yellow

# Check if Maven is installed
try {
    $mvnVersion = mvn --version
    Write-Host "Maven found" -ForegroundColor Green
} catch {
    Write-Host "ERROR: Maven not found. Please install Maven first." -ForegroundColor Red
    exit 1
}

# Check if Java is installed
try {
    $javaVersion = java -version 2>&1
    Write-Host "Java found" -ForegroundColor Green
} catch {
    Write-Host "ERROR: Java not found. Please install Java 17 or higher." -ForegroundColor Red
    exit 1
}

# Clean previous builds
Write-Host "`nCleaning previous builds..." -ForegroundColor Yellow
if (Test-Path "target") {
    Remove-Item -Path "target" -Recurse -Force
}

# Build the project
Write-Host "`nBuilding SiMP3..." -ForegroundColor Yellow
$buildCommand = "mvn clean package"
if ($SkipTests) {
    $buildCommand += " -DskipTests"
}

$buildProcess = Start-Process -FilePath "cmd.exe" -ArgumentList "/c", $buildCommand -NoNewWindow -PassThru -Wait
if ($buildProcess.ExitCode -ne 0) {
    Write-Host "ERROR: Build failed!" -ForegroundColor Red
    exit 1
}

Write-Host "Build completed successfully!" -ForegroundColor Green

# Check if the executable was created
$exePath = "target\SiMP3.exe"
if (Test-Path $exePath) {
    Write-Host "`nExecutable created: $exePath" -ForegroundColor Green
    
    # Create release directory
    $releaseDir = "releases\SiMP3-v$Version"
    if (!(Test-Path $releaseDir)) {
        New-Item -ItemType Directory -Path $releaseDir -Force | Out-Null
    }
    
    # Copy executable to release directory
    Copy-Item $exePath -Destination "$releaseDir\SiMP3.exe" -Force
    Write-Host "Executable copied to: $releaseDir\SiMP3.exe" -ForegroundColor Green
    
    # Create a README for the release
    $readmeContent = @"
SiMP3 Music Player v$Version
========================

IMPORTANT: This version requires Java 17 or higher to be installed on your system.

To check if Java is installed, open Command Prompt and run:
  java -version

If Java is not installed, download it from:
  https://adoptium.net/temurin/releases/

Running SiMP3:
- Double-click SiMP3.exe to start the application
- If the application doesn't start, ensure Java 17+ is installed and in your PATH

For a version that includes Java runtime (no installation required), 
please use the installer version from the GitHub releases page.
"@
    
    Set-Content -Path "$releaseDir\README.txt" -Value $readmeContent
    Write-Host "README created in release directory" -ForegroundColor Green
    
    Write-Host "`n=== Release created successfully! ===" -ForegroundColor Green
    Write-Host "Location: $releaseDir" -ForegroundColor Yellow
    Write-Host "`nNOTE: This executable requires Java 17+ to be installed on the target system." -ForegroundColor Yellow
    Write-Host "To create a self-contained installer, run: .\scripts\create-installer.ps1" -ForegroundColor Cyan
} else {
    Write-Host "ERROR: Executable not found at $exePath" -ForegroundColor Red
    exit 1
}