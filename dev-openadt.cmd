@echo off
setlocal EnableExtensions
set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"

if defined OPENADT_BUN (
  set "BUN=%OPENADT_BUN%"
  goto :run
)
set "BUN=%USERPROFILE%\.bun\bin\bun.exe"
if exist "%BUN%" goto :run
set "BUN=bun"
where bun >nul 2>&1
if errorlevel 1 (
  echo dev-openadt: bun not found. Install https://bun.sh or set OPENADT_BUN. 1>&2
  exit /b 1
)

:run
"%BUN%" "%ROOT%\scripts\nx-openadt.ts" %*
exit /b %ERRORLEVEL%
