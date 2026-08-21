@echo off
chcp 65001 >nul
echo ========================================
echo   PMS 池塘生产管理系统 - 一键启动
echo ========================================
echo.

echo [1/3] 启动 MySQL...
start /B "" "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqld.exe" --no-defaults --datadir=F:\mysql-data --shared-memory --enable-named-pipe
timeout /t 5 /nobreak >nul

echo [2/3] 启动后端...
start /B "" cmd /c "cd /d F:\projects\pms\backend ^&^& mvn spring-boot:run"
timeout /t 10 /nobreak >nul

echo [3/3] 启动前端...
start /B "" cmd /c "cd /d F:\projects\pms\frontend ^&^& npm run dev"

echo.
echo ========================================
echo   启动完成!
echo   前端: http://localhost:5173/pms/
echo   后端: http://localhost:8080
echo   MySQL: 127.0.0.1:3306 (用户: pms, 密码: 123456)
echo ========================================
timeout /t 3 /nobreak >nul
exit
