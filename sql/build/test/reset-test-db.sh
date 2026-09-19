#!/usr/bin/env bash
# 重建集成测试用的独立数据库 zhul_erp_test（不会碰开发库 zhul_erp）。
# 用法：sql/build/test/reset-test-db.sh
# 环境变量：DB_HOST / DB_PORT / DB_USER / DB_PASSWORD（默认 localhost / 3306 / root / root）
#
# 建表脚本里写死了库名 zhul_erp，并且带 "drop schema"，直接执行会删掉开发库。
# 所以这里只在 drop / create / use 三种语句里把库名改成 zhul_erp_test，再送给 mysql。
set -euo pipefail

TEST_DB="zhul_erp_test"
HERE="$(cd "$(dirname "$0")" && pwd)"
SQL_DIR="$HERE/../sql"
DATA_DIR="$HERE/../data"

export MYSQL_PWD="${DB_PASSWORD:-root}"
MYSQL=(mysql -h"${DB_HOST:-localhost}" -P"${DB_PORT:-3306}" -u"${DB_USER:-root}" --default-character-set=utf8mb4)

redirect() {
  sed -E \
    -e "s/^(drop schema if exists )zhul_erp;/\1${TEST_DB};/I" \
    -e "s/^(create schema )zhul_erp( )/\1${TEST_DB}\2/I" \
    -e "s/^use zhul_erp;/use ${TEST_DB};/I" \
    "$1"
}

# 建表脚本按版本顺序执行；v1.2 尚未写好时会自动跳过
for f in schema_v1.sql schema_v1.1.sql schema_v1.2.sql; do
  if [ -f "$SQL_DIR/$f" ]; then
    echo "[reset-test-db] $f"
    if [ "$f" = "schema_v1.sql" ]; then
      redirect "$SQL_DIR/$f" | "${MYSQL[@]}"
    else
      # 后续脚本不含 create schema，先 use 再执行
      { echo "use ${TEST_DB};"; redirect "$SQL_DIR/$f"; } | "${MYSQL[@]}"
    fi
  fi
done

for f in data_v1.sql data_v1.2.sql; do
  if [ -f "$DATA_DIR/$f" ]; then
    echo "[reset-test-db] $f"
    redirect "$DATA_DIR/$f" | "${MYSQL[@]}"
  fi
done

echo "[reset-test-db] 完成，表数量：$("${MYSQL[@]}" -N -e "select count(*) from information_schema.tables where table_schema='${TEST_DB}'")"
