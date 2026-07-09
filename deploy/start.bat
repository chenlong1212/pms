@echo off
setlocal

set APP_JAR=backend\target\pms-backend-1.0.0.jar
set LOG_DIR=logs

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

if "%1"=="start" goto start
if "%1"=="stop" goto stop
echo 用法: %0 {start^|stop}
exit /b 1

:start
echo 启动后端...
start "pms-backend" /B java -jar "%APP_JAR%" --spring.profiles.active=prod > "%LOG_DIR%\stdout.log" 2>&1
echo 后端已启动，日志: %LOG_DIR%\stdout.log
goto end

:stop
echo 停止后端...
taskkill /FI "WINDOWTITLE eq pms-backend*" /F >nul 2>&1
for /f "tokens=2" %%i in ('tasklist /FI "IMAGENAME eq java.exe" /FO LIST ^| findstr /I "PID:"') do (
    wmic process where "ProcessId=%%i and CommandLine like '%%pms-backend%%'" delete >nul 2>&1
)
echo 后端已停止
goto end

:end
endlocal
