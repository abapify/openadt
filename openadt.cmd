@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0openadt.ps1" %*
exit /b %ERRORLEVEL%
