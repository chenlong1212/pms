#!/bin/bash
# 一键打包：构建前端静态资源与后端 jar

set -e

export PATH="/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin:$PATH"

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FRONTEND_DIST="$PROJECT_ROOT/frontend/dist"
BACKEND_JAR="$PROJECT_ROOT/backend/target/pms-backend-1.0.0.jar"

echo "========================================"
echo "  PMS 一键打包"
echo "========================================"
echo "项目目录: $PROJECT_ROOT"
echo ""

echo ">>> 打包前端 ..."
cd "$PROJECT_ROOT/frontend"
if [ ! -d "node_modules" ]; then
  echo "    安装依赖 ..."
  npm install
fi
npm run build
echo "    完成: $FRONTEND_DIST/"

echo ""
echo ">>> 打包后端 ..."
cd "$PROJECT_ROOT/backend"
mvn clean package -DskipTests
echo "    完成: $BACKEND_JAR"

echo ""
echo "========================================"
echo "  打包完成"
echo "========================================"
echo "前端产物: frontend/dist/"
echo "后端产物: backend/target/pms-backend-1.0.0.jar"
echo ""
echo "部署参考:"
echo "  前端 -> Nginx html/pms/"
echo "  后端 -> java -jar pms-backend-1.0.0.jar --spring.profiles.active=prod"
echo "========================================"
echo ""

read -r -p "按 Enter 退出..." _
