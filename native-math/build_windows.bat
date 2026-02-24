@echo off
setlocal
powershell -ExecutionPolicy Bypass -File "%~dp0build.ps1" %*
endlocal
