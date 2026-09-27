#!/usr/bin/env bash
# 彻底重建集成测试用的独立数据库 zhul_erp_test（不会碰开发库 zhul_erp）。
# 平时不需要手动跑这个：Flyway 在 Spring 测试上下文启动时会自动建库/建表/升级
# （见 zhul-erp-backend/src/test/resources/application-test.yml、
# zhul-erp-backend/src/main/resources/db/migration）。
# 只有在迁移脚本改坏、或想彻底清掉历史数据重新来一遍时才需要执行这个脚本：
# 它只是删库，下一次跑测试时 Flyway 会照常自动重建。
#
# 用法：sql/build/test/reset-test-db.sh
# 环境变量：DB_HOST / DB_PORT / DB_USER / DB_PASSWORD（默认 localhost / 3306 / root / root）
set -euo pipefail

TEST_DB="zhul_erp_test"
export MYSQL_PWD="${DB_PASSWORD:-root}"
MYSQL=(mysql -h"${DB_HOST:-localhost}" -P"${DB_PORT:-3306}" -u"${DB_USER:-root}" --default-character-set=utf8mb4)

echo "[reset-test-db] 删除 ${TEST_DB}（如果存在）"
"${MYSQL[@]}" -e "drop schema if exists ${TEST_DB};"
echo "[reset-test-db] 完成。下次跑集成测试时 Flyway 会自动重建表结构。"
