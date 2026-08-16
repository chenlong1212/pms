@echo off
setlocal
title PMS Production Deployment
cd /d "%~dp0"

"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\deploy-prod.ps1"
set "PMS_DEPLOY_EXIT=%ERRORLEVEL%"

echo.
if "%PMS_DEPLOY_EXIT%"=="0" (
  echo [SUCCESS] PMS production deployment completed.
) else (
  echo [FAILED] Deployment did not complete. Review the error above.
)
echo.
pause
exit /b %PMS_DEPLOY_EXIT%
