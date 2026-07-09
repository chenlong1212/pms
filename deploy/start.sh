#!/bin/bash
set -e

APP_NAME="pms-backend"
APP_JAR="backend/target/${APP_NAME}-1.0.0.jar"
PID_FILE="app.pid"
LOG_DIR="logs"

mkdir -p "$LOG_DIR"

start() {
    if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
        echo "应用已在运行 (PID: $(cat "$PID_FILE"))"
        exit 1
    fi
    echo "启动应用..."
    nohup java -jar "$APP_JAR" --spring.profiles.active=prod > "$LOG_DIR/stdout.log" 2>&1 &
    echo $! > "$PID_FILE"
    echo "应用已启动 (PID: $(cat "$PID_FILE"))"
}

stop() {
    if [ ! -f "$PID_FILE" ]; then
        echo "应用未运行"
        exit 1
    fi
    echo "停止应用..."
    kill "$(cat "$PID_FILE")"
    rm -f "$PID_FILE"
    echo "应用已停止"
}

restart() {
    stop 2>/dev/null || true
    start
}

status() {
    if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
        echo "应用运行中 (PID: $(cat "$PID_FILE"))"
    else
        echo "应用未运行"
    fi
}

case "$1" in
    start)   start ;;
    stop)    stop ;;
    restart) restart ;;
    status)  status ;;
    *)       echo "用法: $0 {start|stop|restart|status}" ;;
esac
