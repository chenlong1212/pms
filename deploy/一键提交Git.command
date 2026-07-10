#!/bin/bash
# 一键提交 Git：查看状态 → 暂存 → 查看历史 → 输入说明 → 提交 → 推送

set -euo pipefail

export PATH="/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin:$PATH"

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_ROOT"

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

echo "========================================"
echo "  PMS 一键提交 Git"
echo "========================================"
echo "项目目录: $PROJECT_ROOT"
echo ""

echo ">>> 第一步: 查看当前修改 (git status)"
run_cmd git status

echo ""
read -r -p ">>> 按 Enter 继续执行 git add . ..." _

echo ""
echo ">>> 第二步: 暂存所有修改 (git add .)"
run_cmd git add .

echo ""
echo ">>> 第三步: 查看历史提交 (git log --oneline)"
run_cmd git log --oneline -10

echo ""
echo ">>> 第四步: 输入本次提交说明"
read -r -p "commit -m \"内容\": " COMMIT_MSG

if [ -z "$COMMIT_MSG" ]; then
  echo ">>> 错误: 提交说明不能为空"
  read -r -p "按 Enter 退出..." _
  exit 1
fi

echo ""
read -r -p ">>> 按 Enter 执行 git commit -m \"$COMMIT_MSG\" ..." _

echo ""
echo ">>> 第五步: 提交 (git commit -m \"...\")"
run_cmd git commit -m "$COMMIT_MSG"

echo ""
read -r -p ">>> 按 Enter 执行 git push ..." _

echo ""
echo ">>> 第六步: 推送到远程 (git push)"
run_cmd git push

echo ""
echo "========================================"
echo "  Git 提交完成"
echo "========================================"
echo ""
echo "本次复习的指令:"
echo "  1. git status              # 查看工作区状态"
echo "  2. git add .               # 暂存所有修改"
echo "  3. git log --oneline       # 查看提交历史"
echo "  4. git commit -m \"说明\"    # 本地提交"
echo "  5. git push                # 推送到远程"
echo ""

read -r -p "按 Enter 退出..." _
