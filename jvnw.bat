@echo off
setlocal

if "%JVN_WRAPPER_RAW%"=="1" (
  call "%~dp0gradlew.bat" %*
  exit /b %ERRORLEVEL%
)

echo Welcome to JVN.
echo Thanks for choosing our engine to build with.
echo.
call "%~dp0gradlew.bat" %*
exit /b %ERRORLEVEL%
