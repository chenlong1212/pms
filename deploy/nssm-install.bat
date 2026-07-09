@echo off
chcp 65001 >nul
setlocal

REM ========== 按服务器实际情况修改以下路径 ==========
set SERVICE_NAME=PmsBackend
set JAVA_HOME=C:\Program Files\Java\jdk-17
set APP_DIR=C:\pms
set APP_JAR=%APP_DIR%\pms-backend-1.0.0.jar
set LOG_DIR=%APP_DIR%\logs
REM ==================================================

set JAVA_EXE=%JAVA_HOME%\bin\java.exe

if not exist "%JAVA_EXE%" (
    echo [错误] 找不到 Java: %JAVA_EXE%
    echo 请修改脚本中的 JAVA_HOME
    exit /b 1
)

if not exist "%APP_JAR%" (
    echo [错误] 找不到 jar: %APP_JAR%
    echo 请先将 pms-backend-1.0.0.jar 复制到 %APP_DIR%
    exit /b 1
)

where nssm >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 nssm 命令，请先安装 NSSM 并加入 PATH
    echo 下载: https://nssm.cc/download
    exit /b 1
)

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

echo 安装 Windows 服务: %SERVICE_NAME%
echo Java: %JAVA_EXE%
echo Jar:  %APP_JAR%

nssm stop %SERVICE_NAME% >nul 2>&1
nssm remove %SERVICE_NAME% confirm >nul 2>&1

nssm install %SERVICE_NAME% "%JAVA_EXE%"
nssm set %SERVICE_NAME% AppDirectory "%APP_DIR%"
nssm set %SERVICE_NAME% AppParameters "-jar \"%APP_JAR%\" --spring.profiles.active=prod"
nssm set %SERVICE_NAME% AppStdout "%LOG_DIR%\stdout.log"
nssm set %SERVICE_NAME% AppStderr "%LOG_DIR%\stderr.log"
nssm set %SERVICE_NAME% AppStdoutCreationDisposition 4
nssm set %SERVICE_NAME% AppStderrCreationDisposition 4
nssm set %SERVICE_NAME% AppRotateFiles 1
nssm set %SERVICE_NAME% AppRotateOnline 1
nssm set %SERVICE_NAME% AppRotateBytes 10485760
nssm set %SERVICE_NAME% Start SERVICE_AUTO_START
nssm set %SERVICE_NAME% DisplayName "PMS 鱼塘后端"
nssm set %SERVICE_NAME% Description "水质监测 Spring Boot 后端服务"

nssm start %SERVICE_NAME%

echo.
echo 服务已安装并启动
echo 查看状态: nssm status %SERVICE_NAME%
echo 查看日志: %LOG_DIR%\stdout.log
echo.
pause
