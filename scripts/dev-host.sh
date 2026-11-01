#!/usr/bin/env bash
# 本机开发：MySQL、Redis、AI 编排服务在 Docker，前端、后端在本机。
# 用法：scripts/dev-host.sh up | down | backend（只重启后端）
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUN="$ROOT/.dev-run"
cd "$ROOT"

load_env() {
  local k
  for k in JWT_SECRET AES_KEY AI_TASK_CALLBACK_TOKEN DB_PASSWORD; do
    export "$(grep -m1 "^$k=" .env)"
  done
  export AI_ORCHESTRATOR_BASE_URL=http://localhost:8899
  export AI_TASK_CALLBACK_BASE_URL=http://host.docker.internal:8080
  export UPLOAD_DIR="$ROOT/storage/uploads" UPLOAD_PRIVATE_DIR="$ROOT/storage/private-uploads"
}

# 只停本机的 java、node，不碰 Docker 的端口转发进程
stop_local() { # 端口
  local pid
  for pid in $(lsof -tiTCP:"$1" -sTCP:LISTEN -P -n 2>/dev/null); do
    case "$(ps -o comm= -p "$pid")" in
      *java*|*node*) kill "$pid" 2>/dev/null ;;
    esac
  done
  local i=0
  while lsof -tiTCP:"$1" -sTCP:LISTEN -P -n >/dev/null 2>&1 && [ "$i" -lt 20 ]; do
    i=$((i + 1)); sleep 1
  done
}

case "${1:-}" in
  up)
    docker compose stop backend frontend
    docker compose --profile app up -d mysql redis ai-orchestrator || exit 1
    load_env
    scripts/dev-up.sh --ai none
    ;;
  down)
    stop_local 8000
    stop_local 8080
    echo "已停止本机前端、后端；Docker 里的 MySQL、Redis、AI 编排服务继续运行"
    ;;
  backend)
    stop_local 8080
    load_env
    mkdir -p "$RUN"
    # shellcheck disable=SC1091
    source scripts/dev-env.sh >/dev/null || exit 1
    (cd zhul-erp-backend && nohup mvn -o -q spring-boot:run > "$RUN/backend.log" 2>&1 &)
    echo "后端启动中，日志：$RUN/backend.log"
    ;;
  *)
    echo "用法：scripts/dev-host.sh up | down | backend" >&2
    exit 2
    ;;
esac
