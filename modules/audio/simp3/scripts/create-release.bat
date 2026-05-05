@echo off
setlocal enabledelayedexpansion

echo === SiMP3 Release Builder (Basic) ===
echo.
echo WARNING: This creates a basic .exe that REQUIRES Java 17+ on the target computer!
echo For a self-contained version, use create-installer.bat instead.
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

REM Check if scripts directory exists
if not exist "scripts\create-release.ps1" (
    echo ERROR: PowerShell script not found at scripts\create-release.ps1
    echo Please ensure all scripts are properly installed.
    pause
    exit /b 1
)

echo All prerequisites found!
echo.
echo This will create a basic executable that requires Java to be installed.
echo.
echo Are you sure you want to continue? (Recommended: Use create-installer.bat instead)
echo.

set /p confirm="Type YES to continue or anything else to cancel: "
if /i not "%confirm%"=="YES" (
    echo Cancelled by user.
    pause
    exit /b 0
)

echo.
echo Starting build process...

REM Run the PowerShell script with proper error handling
powershell -ExecutionPolicy Bypass -File "scripts\create-release.ps1" -Version "1.0.0"

REM Check if PowerShell script succeeded
if %errorlevel% neq 0 (
    echo.
    echo ERROR: Release creation failed!
    echo Please check the error messages above.
    pause
    exit /b 1
)

echo.
echo === Process completed successfully! ===
echo.
echo Basic executable created at: releases\SiMP3-v1.0.0\SiMP3.exe
echo.
echo IMPORTANT REMINDER:
echo - This .exe requires Java 17+ to be installed on the target computer
echo - It will NOT work on computers without Java
echo - For distribution, use create-installer.bat to create a self-contained installer
echo.
pause