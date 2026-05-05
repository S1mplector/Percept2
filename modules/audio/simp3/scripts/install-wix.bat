@echo off
setlocal enabledelayedexpansion

echo === WiX Toolset Installer ===
echo.
echo This will download and install WiX Toolset v3.14 for creating Windows installers.
echo.

REM Check if running as administrator
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo WARNING: Not running as administrator!
    echo Some features may require administrator privileges.
    echo.
)

REM Check if PowerShell is available
where powershell >nul 2>nul
if %errorlevel% neq 0 (
    echo ERROR: PowerShell not found!
    echo PowerShell is required to run this script.
    pause
    exit /b 1
)

REM Check if WiX is already installed
where candle.exe >nul 2>nul
if %errorlevel% equ 0 (
    echo WiX Toolset appears to be already installed!
    echo.
    candle.exe -? 2>&1 | findstr /i "version"
    echo.
    echo Do you want to reinstall it?
    set /p reinstall="Type YES to reinstall or anything else to cancel: "
    if /i not "!reinstall!"=="YES" (
        echo Installation cancelled.
        pause
        exit /b 0
    )
)

REM Check if scripts directory exists
if not exist "scripts\install-wix.ps1" (
    echo ERROR: PowerShell script not found at scripts\install-wix.ps1
    echo Please ensure all scripts are properly installed.
    pause
    exit /b 1
)

echo.
echo Starting WiX Toolset installation...
echo This will download approximately 30MB and may take a few minutes.
echo.

REM Run the PowerShell script with proper error handling
powershell -ExecutionPolicy Bypass -File "scripts\install-wix.ps1"

REM Check if PowerShell script succeeded
if %errorlevel% neq 0 (
    echo.
    echo ERROR: WiX installation failed!
    echo Please check the error messages above.
    echo.
    echo Common issues:
    echo - No internet connection
    echo - Firewall blocking downloads
    echo - Insufficient permissions (try running as administrator)
    echo.
    pause
    exit /b 1
)

REM Verify installation
where candle.exe >nul 2>nul
if %errorlevel% neq 0 (
    echo.
    echo WARNING: WiX tools not found in PATH after installation!
    echo.
    echo You may need to:
    echo 1. Close and reopen this terminal
    echo 2. Log out and log back in to Windows
    echo 3. Manually add WiX to your PATH
    echo.
    echo Expected location: C:\Program Files (x86)\WiX Toolset v3.14\bin
    echo.
    pause
    exit /b 1
)

echo.
echo === WiX Toolset installed successfully! ===
echo.
candle.exe -? 2>&1 | findstr /i "version"
echo.
echo You can now use create-installer.bat to create Windows installers!
echo.
pause