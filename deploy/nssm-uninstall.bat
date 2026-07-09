@echo off
chcp 65001 >nul
setlocal

set SERVICE_NAME=PmsBackend

where nssm >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 nssm 命令
    exit /b 1
)

echo 停止并卸载服务: %SERVICE_NAME%
nssm stop %SERVICE_NAME%
nssm remove %SERVICE_NAME% confirm

echo 完成
pause
