@echo off
echo Applying SiMP3 portable update...
echo.

:: Change to parent directory (where SiMP3.exe should be)
cd /d "%~dp0\.."

:: Wait for the application to fully close
echo Waiting for application to close...
timeout /t 3 /nobreak > nul

:: Kill any remaining SiMP3 processes
taskkill /F /IM SiMP3.exe >nul 2>&1
timeout /t 2 /nobreak > nul

:: Backup current executable
if exist "SiMP3.exe" (
    echo Backing up current version...
    if exist "SiMP3.exe.backup" del /Q "SiMP3.exe.backup"
    move /Y "SiMP3.exe" "SiMP3.exe.backup" > nul 2>&1
)

:: Set update file path
set "updateFile=update\SiMP3.v1.0.0-portable.zip"

:: Check if update file is a zip
if /i "%updateFile:~-4%"==".zip" (
    echo Extracting update...
    :: Use PowerShell to extract, preserving directory structure
    powershell -NoProfile -Command "& { Add-Type -AssemblyName System.IO.Compression.FileSystem; [System.IO.Compression.ZipFile]::ExtractToDirectory('%CD%\%updateFile%', '%CD%'); }"

    :: Check if extraction created a subdirectory
    for /d %%D in (*) do (
        if exist "%%D\SiMP3.exe" (
            echo Moving files from extracted directory...
            xcopy /E /Y "%%D\*" "." > nul
            rmdir /S /Q "%%D"
        )
    )
) else if /i "%updateFile:~-4%"==".exe" (
    :: Check if it's a self-extracting archive or plain executable
    :: Try to extract first (some portable versions are self-extracting)
    echo Checking if update is self-extracting...
    "%updateFile%" /extract /quiet >nul 2>&1
    if errorlevel 1 (
        :: Not self-extracting, just copy the executable
        echo Installing new version...
        copy /Y "%updateFile%" "SiMP3.exe" > nul
    )
) else (
    :: Unknown file type, try to copy
    echo Installing new version...
    copy /Y "%updateFile%" "SiMP3.exe" > nul
)

:: Verify update was successful
if not exist "SiMP3.exe" (
    echo ERROR: Update failed - SiMP3.exe not found!
    if exist "SiMP3.exe.backup" (
        echo Restoring backup...
        move /Y "SiMP3.exe.backup" "SiMP3.exe" > nul
    )
    pause
    exit /b 1
)

:: Clean up update file
if exist "%updateFile%" del /Q "%updateFile%"

:: Clean up backup after successful update
if exist "SiMP3.exe.backup" del /Q "SiMP3.exe.backup"

:: Start updated application
echo Starting SiMP3...
start "" "SiMP3.exe"

:: Clean up update directory
timeout /t 2 /nobreak > nul
rmdir /S /Q "update" 2>nul

:: Exit
exit
