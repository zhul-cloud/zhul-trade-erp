#!/bin/sh
set -eu

# Validate only when starting the application, so diagnostic commands still work.
if [ "${1:-}" = "java" ]; then
    : "${DB_USER:?Set DB_USER in .env}"
    : "${DB_PASSWORD:?Set DB_PASSWORD in .env}"
    : "${JWT_SECRET:?Set JWT_SECRET in .env}"
    : "${AES_KEY:?Set AES_KEY in .env}"
    : "${AI_TASK_CALLBACK_TOKEN:?Set AI_TASK_CALLBACK_TOKEN in .env}"
    if [ "${#JWT_SECRET}" -lt 32 ]; then
        echo "JWT_SECRET must contain at least 32 characters" >&2
        exit 1
    fi
    # An empty DB_URL also falls back to the individual connection settings.
    # For cloud TLS verification/options, provide the complete JDBC URL in DB_URL.
    if [ -z "${DB_URL:-}" ]; then
        DB_URL="jdbc:mysql://${DB_HOST:-mysql}:${DB_PORT:-3306}/${DB_NAME:-zhul_erp}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&connectTimeout=5000&socketTimeout=30000"
        export DB_URL
    fi
fi

exec "$@"
