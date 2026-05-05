# SiMP3 Music Player - PowerShell Packaging Script
# This script builds and packages the application with enhanced features

param(
    [string]$Version = "1.0.0",
    [switch]$SkipTests = $false,
    [switch]$KeepBuildArtifacts = $false,
    [switch]$CreateInstaller = $false
)

# Configuration
$ProjectName = "SiMP3"
$MainClass = "com.musicplayer.Launcher"
$BuildDir = "target"
$DistDir = "dist"
$IconPath = "src\main\resources\images\icons\app.ico"
$MinJavaVersion = 17

# Colors for output
function Write-ColorOutput($ForegroundColor) {
    $fc = $host.UI.RawUI.ForegroundColor
    $host.UI.RawUI.ForegroundColor = $ForegroundColor
    if ($args) {
        Write-Output $args
    }
    $host.UI.RawUI.ForegroundColor = $fc
}

function Write-Success($message) { Write-ColorOutput Green "✓ $message" }
function Write-Error($message) { Write-ColorOutput Red "✗ $message" }
function Write-Warning($message) { Write-ColorOutput Yellow "⚠ $message" }
function Write-Info($message) { Write-ColorOutput Cyan "ℹ $message" }
function Write-Step($step, $total, $message) { Write-ColorOutput White "[$step/$total] $message" }

# Banner
Write-Host ""
Write-ColorOutput Cyan "╔════════════════════════════════════════════╗"
Write-ColorOutput Cyan "║     SiMP3 Music Player Packaging Script    ║"
Write-ColorOutput Cyan "║            Version: $Version               ║"
Write-ColorOutput Cyan "╚════════════════════════════════════════════╝"
Write-Host ""

# Validate environment
Write-Info "Validating build environment..."

# Check for required tools
$requiredTools = @{
    "mvn" = "Maven"
    "java" = "Java"
    "jpackage" = "JPackage (JDK 17+)"
}

$missingTools = @()
foreach ($tool in $requiredTools.Keys) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        $missingTools += $requiredTools[$tool]
    }
}

if ($missingTools.Count -gt 0) {
    Write-Error "Missing required tools: $($missingTools -join ', ')"
    Write-Host "Please install the missing tools and ensure they are in your PATH."
    exit 1
}

# Check Java version
$javaVersionOutput = java -version 2>&1 | Select-String "version"
$javaVersion = [regex]::Match($javaVersionOutput, '"(\d+)').Groups[1].Value
if ([int]$javaVersion -lt $MinJavaVersion) {
    Write-Error "Java $MinJavaVersion or higher is required. Current version: $javaVersion"
    exit 1
}
Write-Success "Java version $javaVersion detected"

# Check if SiMP3 is running
Write-Info "Checking for running instances..."
$runningProcesses = Get-Process -Name $ProjectName -ErrorAction SilentlyContinue
if ($runningProcesses) {
    Write-Warning "$ProjectName is currently running!"
    $response = Read-Host "Do you want to terminate it? (Y/N)"
    if ($response -eq 'Y') {
        $runningProcesses | Stop-Process -Force
        Start-Sleep -Seconds 2
        Write-Success "Terminated $ProjectName processes"
    } else {
        Write-Error "Cannot proceed while $ProjectName is running"
        exit 1
    }
}

# Create timestamp
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$buildInfo = @{
    project = $ProjectName
    version = $Version
    buildDate = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    buildSystem = $env:COMPUTERNAME
    javaVersion = $javaVersion
    timestamp = $timestamp
}

# Clean previous builds
Write-Step 1 8 "Cleaning previous builds..."
if (Test-Path $DistDir) {
    try {
        Remove-Item -Path $DistDir -Recurse -Force -ErrorAction Stop
    } catch {
        Write-Error "Could not remove dist folder. It may be open in Explorer."
        Write-Host "Please close any windows showing the dist folder and try again."
        exit 1
    }
}
New-Item -ItemType Directory -Path $DistDir -Force | Out-Null

if (-not $KeepBuildArtifacts -and (Test-Path $BuildDir)) {
    Remove-Item -Path "$BuildDir\*" -Recurse -Force -ErrorAction SilentlyContinue
}

# Build the project
Write-Step 2 8 "Building project with Maven..."
$mvnArgs = @("clean", "package")
if ($SkipTests) {
    $mvnArgs += "-DskipTests"
}

$mvnProcess = Start-Process -FilePath "mvn" -ArgumentList $mvnArgs -NoNewWindow -PassThru -Wait
if ($mvnProcess.ExitCode -ne 0) {
    Write-Error "Maven build failed!"
    exit 1
}

# Verify JAR was created
$jarFile = "$BuildDir\$ProjectName-$Version.jar"
if (-not (Test-Path $jarFile)) {
    Write-Error "Expected JAR file not found: $jarFile"
    exit 1
}
Write-Success "JAR file created successfully"

# Create jpackage input directory
Write-Step 3 8 "Preparing jpackage input..."
$jpackageInput = "$BuildDir\jpackage-input"
if (Test-Path $jpackageInput) {
    Remove-Item -Path $jpackageInput -Recurse -Force
}
New-Item -ItemType Directory -Path $jpackageInput -Force | Out-Null

# Copy artifacts
Copy-Item -Path $jarFile -Destination $jpackageInput
if (Test-Path "$BuildDir\lib") {
    Copy-Item -Path "$BuildDir\lib" -Destination "$jpackageInput\lib" -Recurse
}

# Build self-contained application
Write-Step 4 8 "Building self-contained application with jpackage..."
$appImagePath = "$BuildDir\$ProjectName"
if (Test-Path $appImagePath) {
    Remove-Item -Path $appImagePath -Recurse -Force -ErrorAction SilentlyContinue
}

# Prepare jpackage arguments
$jpackageArgs = @(
    "--type", "app-image",
    "--name", $ProjectName,
    "--app-version", $Version,
    "--input", $jpackageInput,
    "--main-jar", "$ProjectName-$Version.jar",
    "--main-class", $MainClass,
    "--java-options", "--add-modules=ALL-MODULE-PATH",
    "--java-options", "-Xmx512m",
    "--dest", $BuildDir,
    "--vendor", "SiMP3 Development Team",
    "--description", "A modern music player for your digital library"
)

# Add icon if exists
if (Test-Path $IconPath) {
    $jpackageArgs += "--icon", $IconPath
} else {
    Write-Warning "Icon file not found at $IconPath"
}

# Run jpackage
$jpackageProcess = Start-Process -FilePath "jpackage" -ArgumentList $jpackageArgs -NoNewWindow -PassThru -Wait
if ($jpackageProcess.ExitCode -ne 0) {
    Write-Error "jpackage build failed!"
    exit 1
}

# Verify output
if (-not (Test-Path "$appImagePath\$ProjectName.exe")) {
    Write-Error "jpackage did not create expected executable!"
    exit 1
}
Write-Success "Self-contained application created"

# Create portable ZIP package
Write-Step 5 8 "Creating portable ZIP package..."
$zipName = "$ProjectName-v$Version-portable-win64.zip"
$zipPath = "$DistDir\$zipName"

try {
    Compress-Archive -Path "$appImagePath\*" -DestinationPath $zipPath -Force
    Write-Success "ZIP package created: $zipName"
} catch {
    Write-Error "Failed to create ZIP package: $_"
    
    # Try 7-Zip as fallback
    if (Get-Command "7z" -ErrorAction SilentlyContinue) {
        Write-Info "Trying 7-Zip as fallback..."
        & 7z a -tzip $zipPath "$appImagePath\*" | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Success "ZIP package created with 7-Zip"
        } else {
            Write-Error "Both PowerShell and 7-Zip methods failed!"
            exit 1
        }
    } else {
        exit 1
    }
}

# Create additional packages
Write-Step 6 8 "Creating additional distribution packages..."

# Check for Launch4j
if (Test-Path "launch4j-config.xml") {
    if (Get-Command "launch4jc" -ErrorAction SilentlyContinue) {
        Write-Info "Building Launch4j executable..."
        & launch4jc launch4j-config.xml
        if (Test-Path "$BuildDir\$ProjectName.exe") {
            Copy-Item "$BuildDir\$ProjectName.exe" "$DistDir\$ProjectName-v$Version-requires-java.exe"
            Write-Success "Launch4j executable created"
        }
    } else {
        Write-Warning "Launch4j not found, skipping..."
    }
}

# Create batch launcher
$batchContent = @"
@echo off
:: $ProjectName Launcher - Requires Java $MinJavaVersion+

:: Check Java installation
java -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java is not installed or not in PATH
    echo Please install Java $MinJavaVersion or higher from https://adoptium.net/
    echo.
    pause
    exit /b 1
)

:: Launch application
java -jar "%~dp0$ProjectName-$Version.jar" %*

if errorlevel 1 (
    echo.
    echo Application exited with an error.
    pause
)
"@

$batchContent | Out-File -FilePath "$DistDir\$ProjectName-v$Version-launcher.bat" -Encoding ASCII
Copy-Item $jarFile "$DistDir\"

# Create Windows installer if requested
if ($CreateInstaller) {
    Write-Step 7 8 "Creating Windows installer..."
    if (Get-Command "jpackage" -ErrorAction SilentlyContinue) {
        $installerArgs = @(
            "--type", "msi",
            "--name", $ProjectName,
            "--app-version", $Version,
            "--input", $jpackageInput,
            "--main-jar", "$ProjectName-$Version.jar",
            "--main-class", $MainClass,
            "--icon", $IconPath,
            "--dest", $DistDir,
            "--win-menu",
            "--win-shortcut",
            "--vendor", "SiMP3 Development Team",
            "--description", "A modern music player for your digital library"
        )
        
        & jpackage $installerArgs
        if ($LASTEXITCODE -eq 0) {
            Write-Success "Windows installer created"
        } else {
            Write-Warning "Failed to create installer"
        }
    }
} else {
    Write-Step 7 8 "Skipping installer creation (use -CreateInstaller to enable)"
}

# Create documentation
Write-Step 8 8 "Creating distribution documentation..."

# Create README
$readmeContent = @"
$ProjectName Music Player v$Version
=====================================

Thank you for downloading $ProjectName!

DISTRIBUTION FILES:
-------------------

1. $zipName (Recommended)
   - Self-contained portable application
   - Includes Java runtime - no installation required
   - Just extract and run $ProjectName.exe
   - Size: ~200 MB

2. $ProjectName-v$Version-launcher.bat + $ProjectName-$Version.jar
   - Batch launcher with JAR file
   - Requires Java $MinJavaVersion or higher to be installed
   - Run the .bat file to start the application

$(if (Test-Path "$DistDir\$ProjectName-v$Version-requires-java.exe") {
"3. $ProjectName-v$Version-requires-java.exe
   - Standalone executable
   - Requires Java $MinJavaVersion or higher to be installed
   - Single file launcher"
})

$(if (Test-Path "$DistDir\$ProjectName-$Version.msi") {
"4. $ProjectName-$Version.msi
   - Windows installer
   - Installs the application with Start Menu shortcuts
   - Includes Java runtime"
})

SYSTEM REQUIREMENTS:
--------------------
- Windows 10/11 (64-bit)
- 4 GB RAM minimum
- 500 MB free disk space
- Audio output device

GETTING STARTED:
----------------
For portable version:
1. Extract $zipName to any folder
2. Run $ProjectName.exe from the extracted folder
3. Add your music library folder when prompted

For Java-required version:
1. Ensure Java $MinJavaVersion+ is installed (java -version)
2. Run the executable or batch file
3. Add your music library folder when prompted

FEATURES:
---------
- Modern, intuitive interface
- Support for MP3, FLAC, WAV, OGG formats
- Playlist management
- Album artwork display
- Audio visualizer
- Search and filter capabilities
- Listening statistics
- Shuffle and repeat modes

TROUBLESHOOTING:
----------------
If the application doesn't start:
- For portable version: Try running as administrator
- For Java version: Verify Java is installed and in PATH
- Check Windows Defender/Antivirus isn't blocking the app
- Ensure all files were extracted (for ZIP version)

SUPPORT:
--------
Report issues at: https://github.com/yourusername/simp3/issues

Build Information:
-----------------
Build Date: $($buildInfo.buildDate)
Build System: $($buildInfo.buildSystem)
Java Version: $($buildInfo.javaVersion)

Enjoy your music!
"@

$readmeContent | Out-File -FilePath "$DistDir\README.txt" -Encoding UTF8

# Create checksums
Write-Info "Creating checksums..."
$checksumContent = @"
$ProjectName v$Version - File Checksums
=====================================
Generated: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")

SHA-256 Checksums:
-----------------
"@

Get-ChildItem $DistDir -File | Where-Object { $_.Name -notin @("checksums.txt", "build-info.json") } | ForEach-Object {
    $hash = (Get-FileHash -Path $_.FullName -Algorithm SHA256).Hash
    $checksumContent += "`n$hash  $($_.Name)"
}

$checksumContent | Out-File -FilePath "$DistDir\checksums.txt" -Encoding UTF8

# Save build info
$buildInfo | ConvertTo-Json | Out-File -FilePath "$DistDir\build-info.json" -Encoding UTF8

# Summary
Write-Host ""
Write-ColorOutput Green "╔════════════════════════════════════════════╗"
Write-ColorOutput Green "║         Packaging Complete!                ║"
Write-ColorOutput Green "╚════════════════════════════════════════════╝"
Write-Host ""

Write-Info "Distribution files created in: $(Resolve-Path $DistDir)"
Write-Host ""
Write-Host "Files ready for distribution:"
Write-Host "-----------------------------"
Get-ChildItem $DistDir | ForEach-Object {
    $sizeMB = [math]::Round($_.Length / 1MB, 2)
    Write-Host "$($_.Name) - $sizeMB MB"
}

Write-Host ""
$totalSize = [math]::Round((Get-ChildItem $DistDir -Recurse | Measure-Object -Property Length -Sum).Sum / 1MB, 2)
Write-Info "Total distribution size: $totalSize MB"
Write-Host ""

# Open folder if requested
$openFolder = Read-Host "Do you want to open the distribution folder? (Y/N)"
if ($openFolder -eq 'Y') {
    Start-Process explorer.exe -ArgumentList (Resolve-Path $DistDir)
}

Write-Success "Packaging script completed successfully!"