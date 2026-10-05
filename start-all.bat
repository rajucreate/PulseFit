@echo off
REM PulseFit - start all services (Windows)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-all.ps1"
exit /b %ERRORLEVEL%
