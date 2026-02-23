@echo off
setlocal enabledelayedexpansion

echo === SiMP3 Installer Builder ===
echo.

REM Check if running from correct directory
if not exist "pom.xml" (
    echo ERROR: This script must be run from the SiMP3 project root directory!
    echo Current directory: %CD%
    pause
    exit /b 1
)

REM Check if PowerShell is available
where powershell >nul 2>nul
if %errorlevel% neq 0 (
    echo ERROR: PowerShell not found!
    echo PowerShell is required to run this script.
    pause
    exit /b 1
)

REM Check if Maven is available
where mvn >nul 2>nul
if %errorlevel% neq 0 (
    echo ERROR: Maven not found!
    echo Please install Maven and ensure it's in your PATH.
    echo Download from: https://maven.apache.org/download.cgi
    pause
    exit /b 1
)

REM Check if Java is available
where java >nul 2>nul
if %errorlevel% neq 0 (
    echo ERROR: Java not found!
    echo Please install Java 17 or higher.
    echo Download from: https://adoptium.net/
    pause
    exit /b 1
)

REM Check if jpackage is available
where jpackage >nul 2>nul
if %errorlevel% neq 0 (
    echo ERROR: jpackage not found!
    echo Please install JDK 17+ (not just JRE).
    echo The JDK includes jpackage tool needed for creating installers.
    echo Download from: https://adoptium.net/
    pause
    exit /b 1
)

REM Check if scripts directory exists
if not exist "scripts\create-installer.ps1" (
    echo ERROR: PowerShell script not found at scripts\create-installer.ps1
    echo Please ensure all scripts are properly installed.
    pause
    exit /b 1
)

echo All prerequisites found!
echo.
echo This will:
echo 1. Build your project with Maven
echo 2. Create a self-contained installer with Java bundled
echo.
echo Choose installer type:
echo 1. Windows Installer (.exe) - Recommended
echo 2. Windows Installer (.msi)
echo 3. Portable version (no installation)
echo.

set /p choice="Enter your choice (1-3): "

if "%choice%"=="1" (
    set INSTALLER_TYPE=exe
    echo Creating .exe installer...
) else if "%choice%"=="2" (
    set INSTALLER_TYPE=msi
    echo Creating .msi installer...
) else if "%choice%"=="3" (
    set INSTALLER_TYPE=app-image
    echo Creating portable version...
) else (
    echo Invalid choice. Using default (.exe installer)
    set INSTALLER_TYPE=exe
)

echo.
echo Press any key to continue or close this window to cancel...
pause >nul

REM Run the PowerShell script with proper error handling
powershell -ExecutionPolicy Bypass -File "scripts\create-installer.ps1" -Version "1.0.0" -Type "%INSTALLER_TYPE%"

REM Check if PowerShell script succeeded
if %errorlevel% neq 0 (
    echo.
    echo ERROR: Installer creation failed!
    echo Please check the error messages above.
    pause
    exit /b 1
)

echo.
echo === Process completed successfully! ===
echo.

if "%INSTALLER_TYPE%"=="app-image" (
    echo Portable version created at: releases\SiMP3-portable\SiMP3\
    echo You can zip this folder and distribute it.
) else (
    echo Installer created at: releases\installers\
    echo Look for: SiMP3-v1.0.0-installer.%INSTALLER_TYPE%
)

echo.
pause