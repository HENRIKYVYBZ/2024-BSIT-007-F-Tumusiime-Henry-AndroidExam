@echo off
rem Double-click to publish the password-reset page to Firebase Hosting.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0deploy.ps1"
echo.
pause
