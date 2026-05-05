# SiMP3 Package with Runtime Script
# Uses Maven JavaFX plugin to create a runtime image

param(
    [string]$Version = "1.0.0"
)

Write-Host "=== SiMP3 Runtime Package Builder ===" -ForegroundColor Cyan
Write-Host "Version: $Version" -ForegroundColor Yellow

# Check Maven
try {
    $mvnVersion = mvn --version
    Write-Host "Maven found" -ForegroundColor Green
} catch {
    Write-Host "ERROR: Maven not found. Please install Maven first." -ForegroundColor Red
    exit 1
}

# Clean and build
Write-Host "`nCleaning and building project..." -ForegroundColor Yellow
$buildProcess = Start-Process -FilePath "cmd.exe" -ArgumentList "/c", "mvn clean compile" -NoNewWindow -PassThru -Wait
if ($buildProcess.ExitCode -ne 0) {
    Write-Host "ERROR: Build failed!" -ForegroundColor Red
    exit 1
}

# Create runtime image using JavaFX plugin
Write-Host "`nCreating runtime image..." -ForegroundColor Yellow
$jlinkProcess = Start-Process -FilePath "cmd.exe" -ArgumentList "/c", "mvn javafx:jlink" -NoNewWindow -PassThru -Wait
if ($jlinkProcess.ExitCode -ne 0) {
    Write-Host "WARNING: jlink failed. Trying jpackage instead..." -ForegroundColor Yellow
    
    # Try jpackage through Maven
    $jpackageProcess = Start-Process -FilePath "cmd.exe" -ArgumentList "/c", "mvn javafx:jpackage" -NoNewWindow -PassThru -Wait
    if ($jpackageProcess.ExitCode -ne 0) {
        Write-Host "ERROR: jpackage also failed!" -ForegroundColor Red
        exit 1
    }
}

Write-Host "`nPackaging completed!" -ForegroundColor Green

# Check for output
if (Test-Path "target\simp3") {
    Write-Host "Runtime image created at: target\simp3" -ForegroundColor Green
    
    # Create a batch file to run the application
    $batchContent = @"
@echo off
cd /d "%~dp0"
bin\java.exe --module-path lib --add-modules ALL-MODULE-PATH -cp "lib\*" com.musicplayer.Launcher %*
"@
    Set-Content -Path "target\simp3\SiMP3.bat" -Value $batchContent
    Write-Host "Created launcher batch file" -ForegroundColor Green
}

if (Test-Path "target\jpackage") {
    $installer = Get-ChildItem -Path "target\jpackage" -Filter "*.exe" | Select-Object -First 1
    if ($installer) {
        Write-Host "Installer created at: $($installer.FullName)" -ForegroundColor Green
    }
}

Write-Host "`nDone! Check the target directory for output." -ForegroundColor Green