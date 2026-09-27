#!/usr/bin/env bash
# 停止 dev-up.sh 启动的服务：前端、后端、AI 编排服务。MySQL 和 Redis 是常驻服务，默认不停，加 --all 才一起停。
# 按端口找进程，所以也能停掉手工启动的同端口服务。
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
stop_port() { # 端口 名称
  local pids; pids=$(lsof -tiTCP:"$1" -sTCP:LISTEN -P -n 2>/dev/null)
  if [ -z "$pids" ]; then echo "  - $2 没有在运行（:$1）"; return; fi
  # shellcheck disable=SC2086
  kill $pids 2>/dev/null; sleep 1
  pids=$(lsof -tiTCP:"$1" -sTCP:LISTEN -P -n 2>/dev/null)
  # shellcheck disable=SC2086
  [ -n "$pids" ] && kill -9 $pids 2>/dev/null
  echo "  ✓ 已停止 $2（:$1）"
}
stop_port 8000 前端
stop_port 8080 后端
stop_port 8899 "AI 编排服务"
rm -f "$ROOT"/.dev-run/*.pid
if [ "${1:-}" = "--all" ]; then
  command -v brew >/dev/null && { brew services stop redis >/dev/null 2>&1; brew services stop mysql >/dev/null 2>&1; echo "  ✓ 已停止 MySQL、Redis"; }
fi
