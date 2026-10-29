#!/bin/sh
set -eu

: "${AI_TASK_CALLBACK_TOKEN:?Set AI_TASK_CALLBACK_TOKEN in .env}"

# stub：返回固定假数据，只用于页面联调；其他取值（api，及旧的 subscription）都启动真实服务。
# 服务商、Key、模型、运行方式（API / 订阅）由每个任务携带的租户配置决定，在「业务设置 → AI 设置」中维护。
case "${AI_ORCHESTRATOR_MODE:-api}" in
    stub) exec python /app/ai-orchestrator-stub/server.py ;;
    *)
        # 平台租户可能使用订阅方式；挂载到 HOME 的卷可能是空目录，官方 CLI 登录前就必须有其配置目录。
        umask 077
        mkdir -p "${CODEX_HOME:-/home/zhul/codex}" "${CLAUDE_CONFIG_DIR:-/home/zhul/claude}"
        exec python /app/ai-orchestrator/server.py
        ;;
esac
