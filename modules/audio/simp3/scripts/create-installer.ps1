# SiMP3 Installer Creation Script
# Creates a self-contained installer with bundled Java runtime using jpackage

param(
    [string]$Version = "1.0.0",
    [switch]$SkipBuild = $false,
    [string]$Type = "exe"  # app-image, exe, or msi
)

Write-Host "=== SiMP3 Installer Builder ===" -ForegroundColor Cyan
Write-Host "Version: $Version" -ForegroundColor Yellow
Write-Host "Package Type: $Type" -ForegroundColor Yellow

# Check if Java 17+ with jpackage is available
try {
    $javaVersion = java -version 2>&1
    $jpackageVersion = jpackage --version 2>&1
    Write-Host "Java and jpackage found" -ForegroundColor Green
} catch {
    Write-Host "ERROR: jpackage not found. Please ensure you have JDK 17+ installed (not just JRE)." -ForegroundColor Red
    Write-Host "Download from: https://adoptium.net/temurin/releases/" -ForegroundColor Yellow
    exit 1
}

# Check for WiX if creating exe/msi
if ($Type -eq "exe" -or $Type -eq "msi") {
    # Try to find WiX in common locations
    $wixPaths = @(
        "${env:ProgramFiles(x86)}\WiX Toolset v3.14\bin",
        "${env:ProgramFiles}\WiX Toolset v3.14\bin",
        "${env:ProgramFiles(x86)}\WiX Toolset v3.11\bin",
        "${env:ProgramFiles}\WiX Toolset v3.11\bin"
    )
    
    $wixFound = $false
    foreach ($path in $wixPaths) {
        if (Test-Path "$path\candle.exe") {
            $wixFound = $true
            Write-Host "WiX found at: $path" -ForegroundColor Green
            # Temporarily add to PATH for this session
            $env:Path = "$env:Path;$path"
            break
        }
    }
    
    if (!$wixFound) {
        Write-Host "`nWARNING: WiX Toolset not found in PATH!" -ForegroundColor Yellow
        Write-Host "Trying to continue anyway..." -ForegroundColor Yellow
    }
}

# Build the project if not skipped
if (!$SkipBuild) {
    Write-Host "`nBuilding SiMP3..." -ForegroundColor Yellow
    $buildProcess = Start-Process -FilePath "cmd.exe" -ArgumentList "/c", "mvn clean package -DskipTests" -NoNewWindow -PassThru -Wait
    if ($buildProcess.ExitCode -ne 0) {
        Write-Host "ERROR: Build failed!" -ForegroundColor Red
        exit 1
    }
    Write-Host "Build completed successfully!" -ForegroundColor Green
}

# Check if the JAR exists
$jarPath = "target\simp3-$Version.jar"
if (!(Test-Path $jarPath)) {
    # Try with shaded JAR name
    $jarPath = Get-ChildItem -Path "target" -Filter "*.jar" | Where-Object { $_.Name -notlike "*original*" } | Select-Object -First 1
    if (!$jarPath) {
        Write-Host "ERROR: JAR file not found in target directory!" -ForegroundColor Red
        exit 1
    }
    $jarPath = $jarPath.FullName
}

Write-Host "`nUsing JAR: $jarPath" -ForegroundColor Yellow

# Prepare directories
$buildDir = "target\jpackage"
$outputDir = if ($Type -eq "app-image") { "releases\SiMP3-portable" } else { "releases\installers" }

if (Test-Path $buildDir) {
    Remove-Item -Path $buildDir -Recurse -Force
}
New-Item -ItemType Directory -Path $buildDir -Force | Out-Null

if (!(Test-Path $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
}

# For app-image, clean existing directory
if ($Type -eq "app-image" -and (Test-Path "$outputDir\SiMP3")) {
    Write-Host "Removing existing portable directory..." -ForegroundColor Yellow
    Remove-Item -Path "$outputDir\SiMP3" -Recurse -Force
}

# Extract dependencies for module path
Write-Host "`nPreparing dependencies..." -ForegroundColor Yellow
$libDir = "$buildDir\lib"
New-Item -ItemType Directory -Path $libDir -Force | Out-Null

# Copy the main JAR
Copy-Item $jarPath -Destination "$buildDir\simp3.jar" -Force

# Create input directory for jpackage
$inputDir = "$buildDir\input"
New-Item -ItemType Directory -Path $inputDir -Force | Out-Null
Copy-Item "$buildDir\simp3.jar" -Destination $inputDir -Force

# Run jpackage
Write-Host "`nCreating package with jpackage..." -ForegroundColor Yellow

$jpackageArgs = @(
    "--type", $Type,
    "--name", "SiMP3",
    "--app-version", $Version,
    "--vendor", "SiMP3 Music Player",
    "--description", "A modern, modular music player",
    "--input", $inputDir,
    "--main-jar", "simp3.jar",
    "--main-class", "com.musicplayer.Launcher",
    "--icon", "src\main\resources\images\icons\app.ico",
    "--dest", $outputDir,
    "--java-options", "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--java-options", "--add-opens=java.base/java.nio=ALL-UNNAMED",
    "--java-options", "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
    "--java-options", "--add-opens=java.base/java.io=ALL-UNNAMED"
)

# Add Windows-specific options for installers
if ($Type -eq "exe" -or $Type -eq "msi") {
    $jpackageArgs += @(
        "--win-dir-chooser",
        "--win-menu",
        "--win-shortcut",
        "--win-per-user-install"
    )
}

Write-Host "Running jpackage with arguments:" -ForegroundColor Gray
$jpackageArgs | ForEach-Object { Write-Host "  $_" -ForegroundColor Gray }

try {
    & jpackage $jpackageArgs 2>&1 | ForEach-Object { Write-Host $_ }
    
    if ($Type -eq "app-image") {
        # For app-image, create a batch launcher
        $appDir = "$outputDir\SiMP3"
        if (Test-Path $appDir) {
            $launcherContent = @"
@echo off
cd /d "%~dp0"
bin\SiMP3.exe %*
"@
            Set-Content -Path "$appDir\SiMP3.bat" -Value $launcherContent
            
            # Create a README for portable version
            $readmeContent = @"
SiMP3 Music Player v$Version - Portable Version
===========================================

This is a portable version of SiMP3 that includes Java runtime.
No installation required!

To run SiMP3:
- Double-click SiMP3.bat
- Or run bin\SiMP3.exe directly

This portable version can be:
- Run from a USB drive
- Copied to any location
- Used without admin rights

All settings and data are stored in the application folder.
"@
            Set-Content -Path "$appDir\README.txt" -Value $readmeContent
            
            Write-Host "`n=== Portable package created successfully! ===" -ForegroundColor Green
            Write-Host "Location: $appDir" -ForegroundColor Yellow
            Write-Host "`nThis package includes Java runtime - no installation required!" -ForegroundColor Green
            Write-Host "Run SiMP3.bat to start the application." -ForegroundColor Cyan
        }
    } else {
        # Find the created installer
        $installerPattern = if ($Type -eq "msi") { "SiMP3-*.msi" } else { "SiMP3-*.exe" }
        $installer = Get-ChildItem -Path $outputDir -Filter $installerPattern -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
        
        if ($installer) {
            # Rename to include version
            $newName = "SiMP3-v$Version-installer.$Type"
            $newPath = Join-Path $outputDir $newName
            Move-Item $installer.FullName -Destination $newPath -Force
            
            Write-Host "`n=== Installer created successfully! ===" -ForegroundColor Green
            Write-Host "Location: $newPath" -ForegroundColor Yellow
            Write-Host "`nThis installer includes Java runtime - no Java installation required!" -ForegroundColor Green
        } else {
            Write-Host "`nChecking for installer file..." -ForegroundColor Yellow
            # Sometimes the file is created without version suffix
            $simpleInstaller = Get-ChildItem -Path $outputDir -Filter "SiMP3.$Type" -ErrorAction SilentlyContinue
            if ($simpleInstaller) {
                $newName = "SiMP3-v$Version-installer.$Type"
                $newPath = Join-Path $outputDir $newName
                Move-Item $simpleInstaller.FullName -Destination $newPath -Force
                
                Write-Host "`n=== Installer created successfully! ===" -ForegroundColor Green
                Write-Host "Location: $newPath" -ForegroundColor Yellow
                Write-Host "`nThis installer includes Java runtime - no Java installation required!" -ForegroundColor Green
            } else {
                Write-Host "WARNING: Installer file not found in $outputDir" -ForegroundColor Yellow
                Write-Host "Files in directory:" -ForegroundColor Gray
                Get-ChildItem $outputDir | ForEach-Object { Write-Host "  $_" -ForegroundColor Gray }
            }
        }
    }
} catch {
    Write-Host "ERROR: jpackage failed!" -ForegroundColor Red
    Write-Host $_.Exception.Message -ForegroundColor Red
    
    if ($Type -ne "app-image") {
        Write-Host "`nTIP: Try running with -Type app-image to create a portable version instead." -ForegroundColor Yellow
    }
    exit 1
}