#!/usr/bin/env bash
# 一键用 Docker 启动开发环境：MySQL、Redis、后端、前端、AI 编排服务。
# 用法：scripts/docker-dev.sh
#   首次运行生成 .env：随机填写三个后端密钥，导入演示账号（platform / admin123），
#   未配置 ANTHROPIC_API_KEY 时 AI 编排服务用 stub 假数据。已有 .env 只补空密钥，其他配置不动。
# 默认不持久化：停止或重建容器后数据清空。详见 docs/06-部署运维/00-快速开始.md。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

rand() { od -An -N32 -tx1 /dev/urandom | tr -d ' \n'; }
get() { sed -n "s/^$1=//p" .env | tail -n 1; }
set_value() { # 键 值：替换已有行，没有则追加
  if grep -q "^$1=" .env; then
    sed -i.bak "s|^$1=.*|$1=$2|" .env && rm -f .env.bak
  else
    printf '%s=%s\n' "$1" "$2" >> .env
  fi
}

command -v docker >/dev/null || { echo "未找到 docker，请先安装 Docker Desktop / Docker Engine" >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo "Docker 没有运行，请先启动 Docker" >&2; exit 1; }

if [ ! -f .env ]; then
  cp .env.example .env
  set_value FLYWAY_LOCATIONS "classpath:db/migration,classpath:db/dev-data"
  [ -z "$(get ANTHROPIC_API_KEY)" ] && set_value AI_ORCHESTRATOR_MODE stub
  echo "== 已生成 .env（开发配置：导入演示账号，AI 使用 stub 假数据）"
fi
for key in JWT_SECRET AES_KEY AI_TASK_CALLBACK_TOKEN; do
  if [ -z "$(get "$key")" ]; then
    set_value "$key" "$(rand)"
    echo "== 已生成 $key"
  fi
done
if [ "$(get AI_ORCHESTRATOR_MODE)" != stub ] && [ -z "$(get ANTHROPIC_API_KEY)" ]; then
  echo "⚠ AI_ORCHESTRATOR_MODE=api 但 ANTHROPIC_API_KEY 为空：AI 解析会失败。填写 Key，或改为 stub" >&2
fi

echo "== 构建并启动（首次需要下载镜像和依赖，可能要几分钟）"
docker compose --profile app up -d --build

port="$(get WEB_PORT)"; port="${port:-8000}"
echo "== 等待后端就绪"
for _ in $(seq 1 60); do
  if curl -fsS "http://localhost:$port/api/v1/public/health" >/dev/null 2>&1; then
    echo
    echo "已就绪：http://localhost:$port"
    echo "演示账号：platform / admin123（仅首次生成 .env 时导入）"
    echo "AI 编排：$(get AI_ORCHESTRATOR_MODE) 模式"
    echo "注意：默认不持久化，停止或重建容器后数据清空。日志：docker compose logs -f backend"
    exit 0
  fi
  sleep 5
done
echo "5 分钟内后端没有就绪，查看日志：docker compose logs --tail=100 backend" >&2
exit 1
