@echo off
setlocal enabledelayedexpansion

:: SiMP3 Music Player - Enhanced Packaging Script
:: This script builds and packages the application with improved error handling

echo ========================================
echo SiMP3 Music Player - Enhanced Packaging Script
echo ========================================
echo.

:: Configuration - Easy to update for new versions
set PROJECT_NAME=SiMP3
set VERSION=1.0.0
set MAIN_CLASS=com.musicplayer.Launcher
set BUILD_DIR=target
set DIST_DIR=dist
set ICON_PATH=src\main\resources\images\icons\app.ico

:: Validate environment
echo [VALIDATION] Checking build environment...

:: Check for Maven
where mvn >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo ERROR: Maven not found in PATH!
    echo Please install Maven and ensure it's in your PATH.
    pause
    exit /b 1
)

:: Check for Java
where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo ERROR: Java not found in PATH!
    echo Please install Java 17+ and ensure it's in your PATH.
    pause
    exit /b 1
)

:: Check for jpackage
where jpackage >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo ERROR: jpackage not found in PATH!
    echo Please ensure you have JDK 17+ installed with jpackage tool.
    pause
    exit /b 1
)

:: Check Java version
for /f tokens^=2-5^ delims^=.-_^" %%j in ('java -version 2^>^&1') do (
    if %%j==version set JAVA_VERSION=%%k
)
if !JAVA_VERSION! LSS 17 (
    echo ERROR: Java 17 or higher is required. Current version: !JAVA_VERSION!
    pause
    exit /b 1
)

:: Check if SiMP3 is running
echo.
echo [SAFETY] Checking if SiMP3 is currently running...
tasklist /FI "IMAGENAME eq SiMP3.exe" 2>NUL | find /I /N "SiMP3.exe">NUL
if "%ERRORLEVEL%"=="0" (
    echo.
    echo WARNING: SiMP3 is currently running!
    echo Please close the application before packaging.
    echo.
    choice /C YN /M "Do you want to terminate SiMP3 now"
    if !ERRORLEVEL! equ 1 (
        echo Terminating SiMP3...
        taskkill /F /IM SiMP3.exe >NUL 2>&1
        timeout /t 3 /nobreak >NUL
    ) else (
        echo Packaging cancelled.
        pause
        exit /b 1
    )
)

:: Create timestamp for build
set TIMESTAMP=%date:~-4%%date:~4,2%%date:~7,2%_%time:~0,2%%time:~3,2%%time:~6,2%
set TIMESTAMP=!TIMESTAMP: =0!

:: Clean previous builds
echo.
echo [1/8] Cleaning previous builds...
if exist "%DIST_DIR%" (
    echo Removing existing dist folder...
    rmdir /s /q "%DIST_DIR%" 2>NUL
    if exist "%DIST_DIR%" (
        echo ERROR: Could not remove dist folder. It may be open in Explorer.
        echo Please close any windows showing the dist folder and try again.
        pause
        exit /b 1
    )
)
mkdir "%DIST_DIR%"

:: Build the project
echo.
echo [2/8] Building project with Maven...
call mvn clean package
if %ERRORLEVEL% neq 0 (
    echo ERROR: Maven build failed!
    echo Check the error messages above for details.
    pause
    exit /b 1
)

:: Verify JAR was created
if not exist "%BUILD_DIR%\%PROJECT_NAME%-%VERSION%.jar" (
    echo ERROR: Expected JAR file not found: %BUILD_DIR%\%PROJECT_NAME%-%VERSION%.jar
    echo Build may have failed or produced different output name.
    pause
    exit /b 1
)

:: Create jpackage input directory
echo.
echo [3/8] Preparing jpackage input...
if exist "%BUILD_DIR%\jpackage-input" rmdir /s /q "%BUILD_DIR%\jpackage-input"
mkdir "%BUILD_DIR%\jpackage-input"

:: Copy main JAR
copy "%BUILD_DIR%\%PROJECT_NAME%-%VERSION%.jar" "%BUILD_DIR%\jpackage-input\" >NUL
if %ERRORLEVEL% neq 0 (
    echo ERROR: Failed to copy JAR to jpackage input!
    pause
    exit /b 1
)

:: Copy any dependency JARs if they exist
if exist "%BUILD_DIR%\lib" (
    echo Copying dependency JARs...
    xcopy /s /q "%BUILD_DIR%\lib\*" "%BUILD_DIR%\jpackage-input\lib\" >NUL
)

:: Build self-contained application
echo.
echo [4/8] Building self-contained application with jpackage...
if exist "%BUILD_DIR%\%PROJECT_NAME%" (
    echo Removing existing build...
    rmdir /s /q "%BUILD_DIR%\%PROJECT_NAME%" 2>NUL
    if exist "%BUILD_DIR%\%PROJECT_NAME%" (
        echo ERROR: Could not remove existing build folder.
        echo Please ensure no SiMP3 instances are running from the target folder.
        pause
        exit /b 1
    )
)

:: Check if icon exists
if not exist "%ICON_PATH%" (
    echo WARNING: Icon file not found at %ICON_PATH%
    echo Continuing without custom icon...
    set ICON_PARAM=
) else (
    set ICON_PARAM=--icon "%ICON_PATH%"
)

:: Run jpackage
jpackage --type app-image ^
    --name %PROJECT_NAME% ^
    --app-version %VERSION% ^
    --input "%BUILD_DIR%\jpackage-input" ^
    --main-jar "%PROJECT_NAME%-%VERSION%.jar" ^
    --main-class %MAIN_CLASS% ^
    %ICON_PARAM% ^
    --java-options "--add-modules=ALL-MODULE-PATH" ^
    --java-options "-Xmx512m" ^
    --dest "%BUILD_DIR%" ^
    --vendor "SiMP3 Development Team" ^
    --description "A modern music player for your digital library"

if %ERRORLEVEL% neq 0 (
    echo ERROR: jpackage build failed!
    echo Common issues:
    echo - Ensure all file paths are correct
    echo - Check that the main class name is correct
    echo - Verify the JAR file structure
    pause
    exit /b 1
)

:: Verify jpackage output
if not exist "%BUILD_DIR%\%PROJECT_NAME%\%PROJECT_NAME%.exe" (
    echo ERROR: jpackage did not create expected executable!
    pause
    exit /b 1
)

:: Create portable ZIP package
echo.
echo [5/8] Creating portable ZIP package...
set ZIP_NAME=%PROJECT_NAME%-v%VERSION%-portable-win64.zip

:: Use PowerShell with progress indication
powershell -Command "& { $ProgressPreference = 'Continue'; Compress-Archive -Path '%BUILD_DIR%\%PROJECT_NAME%\*' -DestinationPath '%DIST_DIR%\%ZIP_NAME%' -Force }"

if %ERRORLEVEL% neq 0 (
    echo ERROR: Failed to create ZIP package!
    echo Trying alternative method...
    
    :: Fallback to 7-Zip if available
    where 7z >nul 2>&1
    if %ERRORLEVEL% equ 0 (
        7z a -tzip "%DIST_DIR%\%ZIP_NAME%" "%BUILD_DIR%\%PROJECT_NAME%\*" >NUL
        if %ERRORLEVEL% neq 0 (
            echo ERROR: Both PowerShell and 7-Zip methods failed!
            pause
            exit /b 1
        )
    ) else (
        echo ERROR: ZIP creation failed and 7-Zip not available as fallback.
        pause
        exit /b 1
    )
)

:: Create Launch4j executable if configuration exists
echo.
echo [6/8] Checking for Launch4j configuration...
if exist "launch4j-config.xml" (
    where launch4jc >nul 2>&1
    if %ERRORLEVEL% equ 0 (
        echo Building Launch4j executable...
        launch4jc launch4j-config.xml
        if exist "%BUILD_DIR%\%PROJECT_NAME%.exe" (
            copy "%BUILD_DIR%\%PROJECT_NAME%.exe" "%DIST_DIR%\%PROJECT_NAME%-v%VERSION%-requires-java.exe" >NUL
        )
    ) else (
        echo Launch4j not found in PATH, skipping...
    )
) else (
    echo No Launch4j configuration found, creating batch launcher instead...
    
    :: Create batch launcher for JAR
    (
    echo @echo off
    echo :: %PROJECT_NAME% Launcher - Requires Java 17+
    echo.
    echo :: Check Java installation
    echo java -version ^>nul 2^>^&1
    echo if errorlevel 1 ^(
    echo     echo ERROR: Java is not installed or not in PATH
    echo     echo Please install Java 17 or higher from https://adoptium.net/
    echo     echo.
    echo     pause
    echo     exit /b 1
    echo ^)
    echo.
    echo :: Launch application
    echo java -jar "%%~dp0%PROJECT_NAME%-%VERSION%.jar" %%*
    echo.
    echo if errorlevel 1 ^(
    echo     echo.
    echo     echo Application exited with an error.
    echo     pause
    echo ^)
    ) > "%DIST_DIR%\%PROJECT_NAME%-v%VERSION%-launcher.bat"
    
    :: Copy JAR for batch launcher
    copy "%BUILD_DIR%\%PROJECT_NAME%-%VERSION%.jar" "%DIST_DIR%\" >NUL
)

:: Create distribution README
echo.
echo [7/8] Creating distribution documentation...
(
echo %PROJECT_NAME% Music Player v%VERSION%
echo =====================================
echo.
echo Thank you for downloading %PROJECT_NAME%!
echo.
echo DISTRIBUTION FILES:
echo -------------------
echo.
echo 1. %ZIP_NAME% ^(Recommended^)
echo    - Self-contained portable application
echo    - Includes Java runtime - no installation required
echo    - Just extract and run %PROJECT_NAME%.exe
echo    - Size: ~200 MB
echo.
if exist "%DIST_DIR%\%PROJECT_NAME%-v%VERSION%-requires-java.exe" (
echo 2. %PROJECT_NAME%-v%VERSION%-requires-java.exe
echo    - Standalone executable
echo    - Requires Java 17 or higher to be installed
echo    - Smaller download size: ~36 MB
echo    - Run this if you already have Java installed
) else (
echo 2. %PROJECT_NAME%-v%VERSION%-launcher.bat + %PROJECT_NAME%-%VERSION%.jar
echo    - Batch launcher with JAR file
echo    - Requires Java 17 or higher to be installed
echo    - Run the .bat file to start the application
)
echo.
echo SYSTEM REQUIREMENTS:
echo --------------------
echo - Windows 10/11 ^(64-bit^)
echo - 4 GB RAM minimum
echo - 500 MB free disk space
echo - Audio output device
echo.
echo GETTING STARTED:
echo ----------------
echo For portable version:
echo 1. Extract %ZIP_NAME% to any folder
echo 2. Run %PROJECT_NAME%.exe from the extracted folder
echo 3. Add your music library folder when prompted
echo.
echo For Java-required version:
echo 1. Ensure Java 17+ is installed ^(java -version^)
echo 2. Run the executable or batch file
echo 3. Add your music library folder when prompted
echo.
echo FEATURES:
echo ---------
echo - Modern, intuitive interface
echo - Support for MP3, FLAC, WAV, OGG formats
echo - Playlist management
echo - Album artwork display
echo - Audio visualizer
echo - Search and filter capabilities
echo - Listening statistics
echo - Shuffle and repeat modes
echo.
echo TROUBLESHOOTING:
echo ----------------
echo If the application doesn't start:
echo - For portable version: Try running as administrator
echo - For Java version: Verify Java is installed and in PATH
echo - Check Windows Defender/Antivirus isn't blocking the app
echo - Ensure all files were extracted ^(for ZIP version^)
echo.
echo SUPPORT:
echo --------
echo Report issues at: https://github.com/yourusername/simp3/issues
echo.
echo Build Information:
echo -----------------
echo Build Date: %date% %time%
echo Build System: %COMPUTERNAME%
echo.
echo Enjoy your music!
) > "%DIST_DIR%\README.txt"

:: Create file checksums
echo.
echo [8/8] Creating checksums and build info...
cd "%DIST_DIR%"

:: Create detailed checksums file
(
echo %PROJECT_NAME% v%VERSION% - File Checksums
echo =====================================
echo Generated: %date% %time%
echo.
echo SHA-256 Checksums:
echo -----------------
) > checksums.txt

for %%f in (*) do (
    if not "%%f"=="checksums.txt" if not "%%f"=="build-info.json" (
        powershell -Command "(Get-FileHash -Path '%%f' -Algorithm SHA256).Hash + '  %%f'" >> checksums.txt
    )
)
cd ..

:: Create build info JSON
(
echo {
echo   "project": "%PROJECT_NAME%",
echo   "version": "%VERSION%",
echo   "buildDate": "%date% %time%",
echo   "buildSystem": "%COMPUTERNAME%",
echo   "javaVersion": "!JAVA_VERSION!",
echo   "timestamp": "%TIMESTAMP%"
echo }
) > "%DIST_DIR%\build-info.json"

:: Summary
echo.
echo ========================================
echo Packaging Complete!
echo ========================================
echo.
echo Distribution files created in: %CD%\%DIST_DIR%\
echo.
echo Files ready for distribution:
echo -----------------------------
dir /B "%DIST_DIR%"
echo.
echo Package sizes:
echo --------------
for %%f in ("%DIST_DIR%\*") do (
    set size=0
    for /f "tokens=3" %%s in ('dir /-c "%%f" ^| findstr /c:"%%~nxf"') do set size=%%s
    set /a sizeMB=!size!/1048576
    echo %%~nxf - !sizeMB! MB
)
echo.
echo Total distribution size:
powershell -Command "$size = (Get-ChildItem '%DIST_DIR%' -Recurse | Measure-Object -Property Length -Sum).Sum / 1MB; Write-Host ('{0:N2} MB' -f $size)"
echo.
echo ========================================
echo.

:: Ask if user wants to open the folder
choice /C YN /M "Do you want to open the distribution folder"
if %ERRORLEVEL% equ 1 (
    start "" "%CD%\%DIST_DIR%"
)

echo.
echo Packaging script completed successfully!
pause