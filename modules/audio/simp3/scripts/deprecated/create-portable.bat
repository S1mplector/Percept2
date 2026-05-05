@echo off
setlocal enabledelayedexpansion

echo === SiMP3 Portable Version Creator ===
echo.
echo This will create a portable version of SiMP3 with bundled Java runtime.
echo No installation required - just extract and run!
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

REM Check Java version
for /f tokens^=2-5^ delims^=.-_^" %%j in ('java -version 2^>^&1') do (
    if %%j==version (
        set "JAVA_VERSION=%%k"
        goto :version_found
    )
)
:version_found

REM Extract major version number
for /f "tokens=1 delims=." %%a in ("%JAVA_VERSION%") do set JAVA_MAJOR=%%a

REM Check if Java 17 or higher
if %JAVA_MAJOR% LSS 17 (
    echo ERROR: Java 17 or higher is required!
    echo Current Java version: %JAVA_VERSION%
    echo Please install Java 17 or higher from: https://adoptium.net/
    pause
    exit /b 1
)

REM Check if jpackage is available
where jpackage >nul 2>nul
if %errorlevel% neq 0 (
    echo ERROR: jpackage not found!
    echo jpackage is included with JDK 14+. Please ensure you have JDK installed, not just JRE.
    echo Your Java version: %JAVA_VERSION%
    echo Download JDK from: https://adoptium.net/
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
echo Java version: %JAVA_VERSION%
echo.

REM Ask for version
set /p VERSION="Enter version number (e.g., 1.0.0): "
if "%VERSION%"=="" (
    echo ERROR: Version number is required!
    pause
    exit /b 1
)

echo.
echo Creating portable version %VERSION%...
echo This may take a few minutes...
echo.

REM Run the PowerShell script for portable version
powershell -ExecutionPolicy Bypass -File "scripts\create-installer.ps1" -Type "app-image" -Version "%VERSION%"

REM Check if PowerShell script succeeded
if %errorlevel% neq 0 (
    echo.
    echo ERROR: Portable version creation failed!
    echo Please check the error messages above.
    pause
    exit /b 1
)

REM Check if output exists
if not exist "releases\SiMP3-v%VERSION%-portable" (
    echo.
    echo ERROR: Expected output directory not found!
    echo Expected: releases\SiMP3-v%VERSION%-portable
    pause
    exit /b 1
)

echo.
echo === Portable version created successfully! ===
echo.
echo Location: releases\SiMP3-v%VERSION%-portable
echo.
echo To use:
echo 1. Copy the entire 'SiMP3-v%VERSION%-portable' folder to any location
echo 2. Run SiMP3.exe from within that folder
echo 3. No installation or Java required!
echo.
echo The folder contains everything needed to run SiMP3, including:
echo - SiMP3.exe (the main executable)
echo - app\ folder (application files and bundled Java runtime)
echo - runtime\ folder (Java runtime files)
echo.
pause