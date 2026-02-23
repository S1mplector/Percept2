@echo off
setlocal enabledelayedexpansion

:: SiMP3 Music Player - Automated Packaging Script
:: This script builds and packages the application for distribution

echo ========================================
echo SiMP3 Music Player - Packaging Script
echo ========================================
echo.

:: Set variables
set PROJECT_NAME=SiMP3
set VERSION=1.0.0
set BUILD_DIR=target
set DIST_DIR=dist
set TIMESTAMP=%date:~-4%%date:~4,2%%date:~7,2%_%time:~0,2%%time:~3,2%%time:~6,2%
set TIMESTAMP=!TIMESTAMP: =0!

:: Clean previous builds
echo [1/7] Cleaning previous builds...
if exist "%DIST_DIR%" rmdir /s /q "%DIST_DIR%"
mkdir "%DIST_DIR%"

:: Build the project
echo.
echo [2/7] Building project with Maven...
call mvn clean package
if %ERRORLEVEL% neq 0 (
    echo ERROR: Maven build failed!
    pause
    exit /b 1
)

:: Create jpackage input directory
echo.
echo [3/7] Preparing jpackage input...
if exist "%BUILD_DIR%\jpackage-input" rmdir /s /q "%BUILD_DIR%\jpackage-input"
mkdir "%BUILD_DIR%\jpackage-input"
copy "%BUILD_DIR%\%PROJECT_NAME%-%VERSION%.jar" "%BUILD_DIR%\jpackage-input\"

:: Build self-contained application
echo.
echo [4/7] Building self-contained application with jpackage...
if exist "%BUILD_DIR%\%PROJECT_NAME%" rmdir /s /q "%BUILD_DIR%\%PROJECT_NAME%"
jpackage --type app-image ^
    --name %PROJECT_NAME% ^
    --app-version %VERSION% ^
    --input "%BUILD_DIR%\jpackage-input" ^
    --main-jar "%PROJECT_NAME%-%VERSION%.jar" ^
    --main-class com.musicplayer.Launcher ^
    --icon "src\main\resources\images\icons\app.ico" ^
    --java-options "--add-modules=ALL-MODULE-PATH" ^
    --dest "%BUILD_DIR%"

if %ERRORLEVEL% neq 0 (
    echo ERROR: jpackage build failed!
    pause
    exit /b 1
)

:: Create portable ZIP package
echo.
echo [5/7] Creating portable ZIP package...
set ZIP_NAME=%PROJECT_NAME%-v%VERSION%-portable-win64.zip
powershell -Command "Compress-Archive -Path '%BUILD_DIR%\%PROJECT_NAME%\*' -DestinationPath '%DIST_DIR%\%ZIP_NAME%' -Force"

:: Copy standalone executable
echo.
echo [6/7] Copying standalone executable (requires Java)...
copy "%BUILD_DIR%\%PROJECT_NAME%.exe" "%DIST_DIR%\%PROJECT_NAME%-v%VERSION%-requires-java.exe"

:: Create distribution README
echo.
echo [7/7] Creating distribution README...
(
echo %PROJECT_NAME% Music Player v%VERSION%
echo =====================================
echo.
echo Thank you for downloading %PROJECT_NAME%!
echo.
echo This package contains:
echo.
echo 1. %ZIP_NAME%
echo    - Self-contained portable application
echo    - No Java installation required
echo    - Extract and run %PROJECT_NAME%.exe from the extracted folder
echo    - Recommended for most users
echo.
echo 2. %PROJECT_NAME%-v%VERSION%-requires-java.exe
echo    - Standalone executable
echo    - Requires Java 17 or higher to be installed
echo    - Single file, smaller download
echo    - For users who already have Java installed
echo.
echo Quick Start:
echo -----------
echo 1. Extract %ZIP_NAME% to your desired location
echo 2. Navigate to the extracted folder
echo 3. Double-click %PROJECT_NAME%.exe to start the application
echo.
echo System Requirements:
echo -------------------
echo - Windows 10/11 64-bit
echo - 4GB RAM minimum
echo - 200MB free disk space
echo.
echo Troubleshooting:
echo ---------------
echo If the application doesn't start:
echo - Ensure you extracted ALL files from the ZIP
echo - Try running as administrator
echo - Check that antivirus isn't blocking the application
echo.
echo Build Date: %date% %time%
echo.
) > "%DIST_DIR%\README.txt"

:: Create file checksums
echo.
echo Creating checksums...
cd "%DIST_DIR%"
for %%f in (*) do (
    if not "%%f"=="checksums.txt" (
        powershell -Command "(Get-FileHash -Path '%%f' -Algorithm SHA256).Hash + '  %%f'" >> checksums.txt
    )
)
cd ..

:: Summary
echo.
echo ========================================
echo Packaging Complete!
echo ========================================
echo.
echo Distribution files created in: %DIST_DIR%\
echo.
echo Files ready for distribution:
echo   - %ZIP_NAME% (Recommended)
echo   - %PROJECT_NAME%-v%VERSION%-requires-java.exe
echo   - README.txt
echo   - checksums.txt
echo.
echo Total size:
powershell -Command "$size = (Get-ChildItem '%DIST_DIR%' -Recurse | Measure-Object -Property Length -Sum).Sum / 1MB; Write-Host ('{0:N2} MB' -f $size)"
echo.
echo ========================================
echo.

:: Open distribution folder
echo Opening distribution folder...
start "" "%DIST_DIR%"

pause