@echo off
setlocal EnableExtensions DisableDelayedExpansion

set "gradlew=%~dp0gradlew.bat"
if not exist "%gradlew%" (
  echo JVN wrapper error: missing "%gradlew%" 1>&2
  exit /b 1
)

set "raw=0"
if "%JVN_WRAPPER_RAW%"=="1" set "raw=1"

if "%raw%"=="0" if "%~1"=="" goto help

if /I "%~1"=="--raw" (
  set "raw=1"
  shift
)

if "%raw%"=="0" (
  if /I "%~1"=="help" goto help
  if /I "%~1"=="-h" goto help
  if /I "%~1"=="--help" goto help
  if "%~1"=="" goto help
  set "command=%~1"
  shift
)

set "forwarded="
set "console_specified=0"
:collect_args
if "%~1"=="" goto dispatch
set "arg_unquoted=%~1"
if /I "%arg_unquoted%"=="--console" set "console_specified=1"
if /I "%arg_unquoted:~0,10%"=="--console=" set "console_specified=1"
set "forwarded=%forwarded% %1"
shift
goto collect_args

:dispatch
if "%raw%"=="1" goto run_raw

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

set "console_arg=--console=plain"
if "%console_specified%"=="1" set "console_arg="

if /I "%command%"=="gradle" goto run_gradle

if defined resolved (
  if defined console_arg (
    call "%gradlew%" %console_arg% %resolved%%forwarded%
  ) else (
    call "%gradlew%" %resolved%%forwarded%
  )
  exit /b %ERRORLEVEL%
)

if defined console_arg (
  call "%gradlew%" %console_arg% %command%%forwarded%
) else (
  call "%gradlew%" %command%%forwarded%
)
exit /b %ERRORLEVEL%

:run_gradle
if defined console_arg (
  call "%gradlew%" %console_arg%%forwarded%
) else (
  call "%gradlew%"%forwarded%
)
exit /b %ERRORLEVEL%

:run_raw
if defined forwarded (
  call "%gradlew%"%forwarded%
) else (
  call "%gradlew%"
)
exit /b %ERRORLEVEL%

:help
echo jvnw
echo.
echo Use jvnw for common tasks. Known commands map to Gradle tasks; everything else passes through.
echo.
echo Common commands:
echo   jvnw launcher   Run the standalone launcher
echo   jvnw editor     Run the editor
echo   jvnw runtime    Run the runtime
echo   jvnw build      Build the workspace
echo   jvnw test       Run the test suite
echo   jvnw check      Run verification tasks
echo   jvnw clean      Remove build outputs
echo   jvnw dist       Create the runtime distribution zip
echo   jvnw jar        Build the runtime jar
echo.
echo Advanced usage:
echo   jvnw gradle ^<gradle-args^>  Pass through to Gradle with wrapper status output
echo   jvnw --raw ^<gradle-args^>   Call gradlew directly with full Gradle output
echo   gradlew ^<gradle-task^>      Optional low-level Gradle entrypoint
echo.
echo Examples:
echo   jvnw launcher
echo   jvnw runtime --args="--script scripts/story/prologue.vns"
echo   jvnw gradle :editor:compileJava
exit /b 0
