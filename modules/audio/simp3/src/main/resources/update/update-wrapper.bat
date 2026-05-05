@echo off
:: SiMP3 Update Wrapper Script
:: This script is used to apply updates after the main application exits

echo SiMP3 Update Wrapper
echo ====================
echo.

:: Wait for the main application to fully exit
echo Waiting for application to close...
timeout /t 3 /nobreak > nul

:: Check if update script exists
if not exist "update\apply-update.bat" (
    echo No update found to apply.
    exit /b 1
)

:: Execute the update script
echo Applying update...
call "update\apply-update.bat"

:: Clean up update directory
if exist "update" (
    rmdir /s /q "update" 2>nul
)

echo Update complete!
exit /b 0