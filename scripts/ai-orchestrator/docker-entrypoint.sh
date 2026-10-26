#!/bin/sh
set -eu

: "${AI_TASK_CALLBACK_TOKEN:?Set AI_TASK_CALLBACK_TOKEN in .env}"

# api：调用 AI 服务商的 API（服务商由 AI_ORCHESTRATOR_PROVIDER 选择，需要 AI_ORCHESTRATOR_API_KEY）；stub：返回固定假数据，只用于页面联调
case "${AI_ORCHESTRATOR_MODE:-api}" in
    api) exec python /app/ai-orchestrator/server.py ;;
    stub) exec python /app/ai-orchestrator-stub/server.py ;;
    *)
        echo "AI_ORCHESTRATOR_MODE must be api or stub" >&2
        exit 1
        ;;
esac
