-- AI 任务解析进度 V1.5.25 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.5.25 的记录）。
ALTER TABLE `ai_task`
    DROP COLUMN `last_progress_at`,
    DROP COLUMN `progress_stage`,
    DROP COLUMN `progress_total`,
    DROP COLUMN `progress_done`;
