-- =====================================================================
-- 生产库时间修正：容器改用北京时间（TZ=Asia/Shanghai）之前写入的时间是 UTC，比北京时间慢 8 小时。
-- 只修正「改时区之前」写入的行：按截止时间筛选，截止时间之后（已按北京时间写入）的不动。
--
-- 用法（在生产库上，用 mysql 客户端执行，按顺序一步一步来）：
--   0. 先备份：mysqldump --single-transaction zhul_erp > zhul_erp_before_tz_fix.sql
--   1. 把下面的 @cutoff 改成「重新部署（改时区）完成」的时间，按改之前的写法，即 UTC 时间。
--      例如北京时间 2026-10-22 20:00 部署完成，就写 '2026-10-22 12:00:00'。
--   2. 执行「第一步」，看哪些表、哪些列有要修正的行，确认都是系统自动记的时间
--      （创建时间、更新时间、登录时间、日志时间等）。如有人手填的时间列，记下来在第二步排除。
--   3. 执行「第二步」，得到逐列的 UPDATE 语句，人工核对后再执行（放在一个事务里）。
-- 不修改 flyway_schema_history（迁移记录）。DATE 类型（只有日期）不修改。
-- =====================================================================

SET @cutoff = '2026-10-22 12:00:00';  -- ← 改成实际部署完成时间（UTC 写法）
SET SESSION group_concat_max_len = 1048576;  -- 表多，拼出的预览语句较长

-- ---------------------------------------------------------------------
-- 第一步：预览。列出当前库所有 datetime / timestamp 列，以及需要修正的行数
-- （执行生成出来的那条 SELECT 语句即可看到每列的行数）
-- ---------------------------------------------------------------------
SELECT GROUP_CONCAT(
         CONCAT('SELECT ''', table_name, ''' AS tbl, ''', column_name, ''' AS col, COUNT(*) AS cnt FROM `',
                table_name, '` WHERE `', column_name, '` < ''', @cutoff, '''')
         SEPARATOR ' UNION ALL ') AS preview_sql
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND data_type IN ('datetime', 'timestamp')
  AND table_name <> 'flyway_schema_history'
  AND table_name IN (SELECT table_name FROM information_schema.tables
                     WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE');

-- ---------------------------------------------------------------------
-- 第二步：生成修正语句。每列一条：早于截止时间的值加 8 小时
-- 若第一步发现有人手填的时间列，在下面的 NOT IN 里补上 'table.column' 排除
-- ---------------------------------------------------------------------
SELECT CONCAT('UPDATE `', table_name, '` SET `', column_name, '` = `', column_name,
              '` + INTERVAL 8 HOUR WHERE `', column_name, '` < ''', @cutoff, ''';') AS fix_sql
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND data_type IN ('datetime', 'timestamp')
  AND table_name <> 'flyway_schema_history'
  AND CONCAT(table_name, '.', column_name) NOT IN ('')  -- ← 需要排除的列写在这里
  AND table_name IN (SELECT table_name FROM information_schema.tables
                     WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE')
ORDER BY table_name, ordinal_position;

-- 执行方式：START TRANSACTION; 粘贴上面生成的 UPDATE 语句; 抽查几行（如 account、login_log）；
-- 确认无误 COMMIT，有问题 ROLLBACK。
