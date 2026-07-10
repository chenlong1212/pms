#!/bin/bash
# 一键运行前后端：清理 5173 / 8080 端口，启动 Spring Boot 与 Vite 开发服务

set -e

export PATH="/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin:$PATH"

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="$PROJECT_ROOT/deploy/logs"
BACKEND_LOG="$LOG_DIR/backend.log"
FRONTEND_LOG="$LOG_DIR/frontend.log"

mkdir -p "$LOG_DIR"

run_cmd() {
  echo ""
  echo "----------------------------------------"
  echo "执行命令: $*"
  echo "----------------------------------------"
  "$@"
  local exit_code=$?
  echo ""
  echo "退出码: $exit_code"
  return $exit_code
}

kill_port() {
  local port=$1
  local pids
  echo ""
  echo "----------------------------------------"
  echo "执行命令: lsof -ti:$port"
  echo "----------------------------------------"
  pids=$(lsof -ti:"$port" 2>/dev/null || true)
  if [ -n "$pids" ]; then
    echo "占用进程: $pids"
    echo ""
    echo "----------------------------------------"
    echo "执行命令: kill -9 $pids"
    echo "----------------------------------------"
    kill -9 $pids 2>/dev/null || true
    echo "退出码: 0"
    sleep 0.5
  else
    echo "端口 $port 未被占用"
    echo "退出码: 0"
  fi
}

cleanup() {
  echo ""
  echo ">>> 正在停止前后端..."
  if [ -n "${BACKEND_PID:-}" ]; then
    echo "执行命令: kill $BACKEND_PID"
    kill "$BACKEND_PID" 2>/dev/null || true
  fi
  if [ -n "${FRONTEND_PID:-}" ]; then
    echo "执行命令: kill $FRONTEND_PID"
    kill "$FRONTEND_PID" 2>/dev/null || true
  fi
  kill_port 5173
  kill_port 8080
  echo ">>> 已退出"
}

trap cleanup INT TERM EXIT

echo "========================================"
echo "  PMS 一键运行前后端"
echo "========================================"
echo "项目目录: $PROJECT_ROOT"
echo ""

echo ">>> 第一步: 清理端口 5173"
kill_port 5173

echo ""
echo ">>> 第二步: 清理端口 8080"
kill_port 8080

echo ""
echo ">>> 第三步: 启动后端 (8080)"
cd "$PROJECT_ROOT/backend"
echo ""
echo "----------------------------------------"
echo "执行命令: SPRING_PROFILES_ACTIVE=local mvn spring-boot:run"
echo "          (后台运行，日志 -> $BACKEND_LOG)"
echo "----------------------------------------"
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run >"$BACKEND_LOG" 2>&1 &
BACKEND_PID=$!
echo "后端 PID: $BACKEND_PID"
echo "退出码: 0"

echo ""
echo ">>> 第四步: 启动前端 (5173)"
cd "$PROJECT_ROOT/frontend"
echo ""
echo "----------------------------------------"
echo "执行命令: npm run dev"
echo "          (后台运行，日志 -> $FRONTEND_LOG)"
echo "----------------------------------------"
npm run dev >"$FRONTEND_LOG" 2>&1 &
FRONTEND_PID=$!
echo "前端 PID: $FRONTEND_PID"
echo "退出码: 0"

echo ""
echo ">>> 第五步: 等待后端就绪"
for i in {1..60}; do
  echo ""
  echo "----------------------------------------"
  echo "执行命令: curl -sf http://localhost:8080/api/device/latest"
  echo "----------------------------------------"
  if curl -sf "http://localhost:8080/api/device/latest" >/dev/null 2>&1; then
    echo "后端已就绪"
    echo "退出码: 0"
    break
  fi
  echo "退出码: 非 0（等待中...）"
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo "后端启动失败，最近日志:"
    tail -n 20 "$BACKEND_LOG"
    exit 1
  fi
  sleep 1
done

echo ""
echo ">>> 第六步: 等待前端就绪"
for i in {1..30}; do
  echo ""
  echo "----------------------------------------"
  echo "执行命令: curl -sf http://localhost:5173/pms/"
  echo "----------------------------------------"
  if curl -sf "http://localhost:5173/pms/" >/dev/null 2>&1; then
    echo "前端已就绪"
    echo "退出码: 0"
    break
  fi
  echo "退出码: 非 0（等待中...）"
  if ! kill -0 "$FRONTEND_PID" 2>/dev/null; then
    echo "前端启动失败，最近日志:"
    tail -n 20 "$FRONTEND_LOG"
    exit 1
  fi
  sleep 1
done

echo ""
echo "========================================"
echo "  前端: http://localhost:5173/pms/"
echo "  后端: http://localhost:8080"
echo "  按 Ctrl+C 停止前后端"
echo "========================================"
echo ""
echo "本次执行的指令复习:"
echo "  lsof -ti:5173            # 查看占用端口的进程"
echo "  kill -9 <PID>            # 强制结束进程"
echo "  mvn spring-boot:run      # 启动后端"
echo "  npm run dev              # 启动前端"
echo "  curl http://localhost:8080/api/device/latest   # 检测后端"
echo "  curl http://localhost:5173/pms/                # 检测前端"
echo ""

echo "----------------------------------------"
echo "执行命令: open http://localhost:5173/pms/"
echo "----------------------------------------"
open "http://localhost:5173/pms/" 2>/dev/null || true
echo "退出码: 0"
echo ""

echo "----------------------------------------"
echo "执行命令: tail -f $BACKEND_LOG $FRONTEND_LOG"
echo "----------------------------------------"
tail -f "$BACKEND_LOG" "$FRONTEND_LOG"
