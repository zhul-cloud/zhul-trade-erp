-- V1.5.23：AI 任务的解析进度（OpenSpec inquiry-large-attachments D11）。
-- 询盘大附件分批解析时，编排服务每完成一批报告一次「已完成 / 总批数」，超时扫描从最近一次进度报告起计时。
-- 回退：sql/build/rollback/V1.5.23__ai_task_progress_rollback.sql（删除这三列）。
ALTER TABLE `ai_task`
    ADD COLUMN `progress_done`    int(11)  NOT NULL DEFAULT 0 COMMENT '分批解析已完成的批数' AFTER `web_searches`,
    ADD COLUMN `progress_total`   int(11)  NOT NULL DEFAULT 0 COMMENT '分批解析的总批数（0 表示没有分批）' AFTER `progress_done`,
    ADD COLUMN `last_progress_at` datetime NULL               COMMENT '最近一次收到进度报告的时间，NULL 表示没有报告过' AFTER `progress_total`;
