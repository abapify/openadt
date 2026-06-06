@echo off
setlocal
cd /d "%~dp0.."
set "BUN=%USERPROFILE%\.bun\bin\bun.exe"
if not exist "%BUN%" (
  echo [openadt-mcp] bun not found at %BUN% >&2
  exit /b 1
)
"%BUN%" run mcp:stdio %*
exit /b %ERRORLEVEL%
