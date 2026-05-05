@echo off
rem -----------------------------------------------------------------------------
rem  jvn.bat — launch the JVN Engine Hub (Swing GUI)
rem
rem  Double-click, or run from any shell:   jvn.bat
rem  Lets you launch the editor, launcher, runtime, build/test the workspace, or
rem  pull-rebase the repository — without typing jvnw commands.
rem -----------------------------------------------------------------------------
setlocal EnableExtensions

for %%I in ("%~dp0.") do set "SCRIPT_DIR=%%~sI\"
set "GRADLEW=%SCRIPT_DIR%gradlew.bat"

if not exist "%GRADLEW%" (
  echo [jvn.bat] error: gradlew.bat not found next to this script. 1>&2
  exit /b 1
)

rem Change to short-path directory to avoid Unicode path issues with Gradle
pushd "%SCRIPT_DIR%"

rem Run the hub via Gradle so classpath + toolchain are handled for us.
rem --console=plain keeps the terminal tidy; -q suppresses Gradle chatter.
rem Use 'start' to detach the GUI so the command prompt closes immediately.
start "" "%GRADLEW%" -q --console=plain :hub:run

popd
exit /b 0
