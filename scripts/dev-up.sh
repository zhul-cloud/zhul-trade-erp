#!/usr/bin/env bash
# 一键启动本机开发所需的全部服务：MySQL、Redis、后端(8080)、前端(8000)、AI 编排服务(8899)。
# 用法：scripts/dev-up.sh [--ai stub|real|none]
#   --ai stub  （默认）免费的假数据占位服务，够跑通询盘解析的页面和联调
#   --ai real  真实的 AI 编排服务，会调用本机 claude CLI，产生真实费用
#   --ai none  不启动 AI 编排服务
# 已经在运行的服务（端口已被占用）会跳过。日志和 PID 在 .dev-run/，停止用 scripts/dev-down.sh。
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUN="$ROOT/.dev-run"
AI=stub
while [ $# -gt 0 ]; do
  case "$1" in
    --ai) AI="${2:-stub}"; shift 2 ;;
    *) echo "未知参数：$1（用法：scripts/dev-up.sh [--ai stub|real|none]）" >&2; exit 2 ;;
  esac
done
case "$AI" in stub|real|none) ;; *) echo "--ai 只能是 stub、real 或 none" >&2; exit 2 ;; esac
mkdir -p "$RUN"

listening() { lsof -iTCP:"$1" -sTCP:LISTEN -P -n >/dev/null 2>&1; }
wait_port() { # 端口 名称 超时秒
  local i=0
  while ! listening "$1"; do
    i=$((i + 1)); [ "$i" -gt "$3" ] && { echo "  ✗ $2 在 ${3}s 内没有起来，看日志：$RUN/$4.log"; return 1; }
    sleep 1
  done
  echo "  ✓ $2 已就绪（:$1）"
}
start_bg() { # 名称 目录 命令...
  local name="$1" dir="$2"; shift 2
  ( cd "$dir" && nohup "$@" > "$RUN/$name.log" 2>&1 & echo $! > "$RUN/$name.pid" )
}

echo "== MySQL / Redis"
if ! listening 3306; then
  command -v brew >/dev/null && brew services start mysql >/dev/null 2>&1
  wait_port 3306 MySQL 30 mysql
else echo "  ✓ MySQL 已在运行（:3306）"; fi
if ! listening 6379; then
  command -v brew >/dev/null && brew services start redis >/dev/null 2>&1
  wait_port 6379 Redis 15 redis
else echo "  ✓ Redis 已在运行（:6379）"; fi

echo "== AI 编排服务（${AI}）"
if [ "$AI" = none ]; then echo "  - 已跳过"
elif listening 8899; then echo "  ✓ 8899 已被占用，视为已在运行"
else
  script="$ROOT/scripts/ai-orchestrator-stub/server.py"; [ "$AI" = real ] && script="$ROOT/scripts/ai-orchestrator/server.py"
  [ "$AI" = real ] && echo "  ⚠ 真实模式：每次解析都会真的调用 claude，产生费用"
  start_bg ai "$ROOT" python3 "$script"
  wait_port 8899 "AI 编排服务($AI)" 15 ai
fi

echo "== 后端"
if listening 8080; then echo "  ✓ 8080 已被占用，视为已在运行"
else
  # shellcheck disable=SC1091
  source "$ROOT/scripts/dev-env.sh" >/dev/null || exit 1
  start_bg backend "$ROOT/zhul-erp-backend" mvn -o -q spring-boot:run
  wait_port 8080 后端 120 backend
fi

echo "== 前端"
if listening 8000; then echo "  ✓ 8000 已被占用，视为已在运行"
else
  start_bg frontend "$ROOT/zhul-erp-frontend" npm run dev
  wait_port 8000 前端 90 frontend
fi

echo
echo "全部就绪：前端 http://localhost:8000  后端 http://localhost:8080  （Swagger：/doc.html）"
echo "登录：admin / admin123（租户账号）；platform / admin123（平台账号，可维护商品主数据）"
echo "停止：scripts/dev-down.sh    日志：$RUN/*.log"
