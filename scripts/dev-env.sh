#!/usr/bin/env bash
# 本机开发环境：固定后端 JDK 17，并检查 MySQL、Redis 是否可用。
# 用法：source scripts/dev-env.sh
# 本机 Maven 默认会拿到更高版本的 JDK，编译会失败，所以必须显式指定 JAVA_HOME。

_ZHUL_JDK17="${ZHUL_JDK17:-/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
if [ ! -x "$_ZHUL_JDK17/bin/java" ]; then
  echo "[dev-env] 找不到 JDK 17：$_ZHUL_JDK17（可用 ZHUL_JDK17 指定路径）" >&2
  return 1 2>/dev/null || exit 1
fi
export JAVA_HOME="$_ZHUL_JDK17"
export PATH="$JAVA_HOME/bin:$PATH"

_mysql_ok=no
if command -v mysql >/dev/null 2>&1 && \
   MYSQL_PWD="${DB_PASSWORD:-root}" mysql -h"${DB_HOST:-localhost}" -P"${DB_PORT:-3306}" -u"${DB_USER:-root}" -e 'select 1' >/dev/null 2>&1; then
  _mysql_ok=yes
fi
_redis_ok=no
if command -v redis-cli >/dev/null 2>&1 && [ "$(redis-cli -h "${REDIS_HOST:-localhost}" -p "${REDIS_PORT:-6379}" ping 2>/dev/null)" = "PONG" ]; then
  _redis_ok=yes
fi

echo "[dev-env] JAVA_HOME=$JAVA_HOME"
echo "[dev-env] MySQL: $_mysql_ok  Redis: $_redis_ok"
[ "$_mysql_ok" = yes ] && [ "$_redis_ok" = yes ] || echo "[dev-env] 警告：MySQL 或 Redis 不可用，集成测试会失败" >&2
unset _ZHUL_JDK17 _mysql_ok _redis_ok
