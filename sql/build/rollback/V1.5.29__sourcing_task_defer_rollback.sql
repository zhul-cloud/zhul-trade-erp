-- 询价分配延后 V1.5.29 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.5.29 的记录）。
-- 回退后延后中的任务立即回到待分配；延后日期、原因随列一起丢弃，回退前如需保留请先导出：
--   SELECT `id`, `task_code`, `defer_until`, `defer_reason`, `defer_by`, `defer_time` FROM `sourcing_task` WHERE `defer_until` IS NOT NULL;
ALTER TABLE `sourcing_task`
    DROP INDEX `idx_sourcing_task_defer_until`,
    DROP COLUMN `defer_time`, DROP COLUMN `defer_by`, DROP COLUMN `defer_reason`, DROP COLUMN `defer_until`;
