@echo off
setlocal EnableExtensions

if "%JVN_WRAPPER_RAW%"=="1" (
  call "%~dp0gradlew.bat" %*
  exit /b %ERRORLEVEL%
)

if "%~1"=="" goto help
if /I "%~1"=="--raw" (
  shift
  call "%~dp0gradlew.bat" %*
  exit /b %ERRORLEVEL%
)
if /I "%~1"=="help" goto help
if /I "%~1"=="-h" goto help
if /I "%~1"=="--help" goto help

set "command=%~1"
shift
set "resolved="

if /I "%command%"=="launcher" set "resolved=:editor:runLauncher"
if /I "%command%"=="editor" set "resolved=:editor:run"
if /I "%command%"=="runtime" set "resolved=:runtime:run"
if /I "%command%"=="run" set "resolved=:runtime:run"
if /I "%command%"=="game" set "resolved=:runtime:run"
if /I "%command%"=="build" set "resolved=build"
if /I "%command%"=="test" set "resolved=test"
if /I "%command%"=="check" set "resolved=check"
if /I "%command%"=="clean" set "resolved=clean"
if /I "%command%"=="dist" set "resolved=:runtime:distZip"
if /I "%command%"=="jar" set "resolved=:runtime:jar"

echo Welcome to JVN.
echo Thanks for choosing our engine to build with.
echo.

if /I "%command%"=="gradle" (
  call "%~dp0gradlew.bat" --console=plain %*
  exit /b %ERRORLEVEL%
)

if defined resolved (
  call "%~dp0gradlew.bat" --console=plain %resolved% %*
  exit /b %ERRORLEVEL%
)

call "%~dp0gradlew.bat" --console=plain %command% %*
exit /b %ERRORLEVEL%

:help
echo JVN wrapper
echo.
echo Use jvnw for day-to-day work. Common commands:
echo   jvnw launcher   Run the standalone launcher
echo   jvnw editor     Run the editor
echo   jvnw runtime    Run the runtime
echo   jvnw build      Build the workspace
echo   jvnw test       Run the test suite
echo   jvnw dist       Create the runtime distribution zip
echo   jvnw jar        Build the runtime jar
echo.
echo Advanced usage:
echo   jvnw gradle ^<gradle-args^>  Pass straight through to Gradle with JVN console styling
echo   jvnw --raw ^<gradle-args^>   Bypass JVN styling and call gradlew directly
echo   gradlew ^<gradle-task^>      Optional low-level Gradle entrypoint
exit /b 0
