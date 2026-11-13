-- V1.5.29：询价分配延后（2026-11-13 反馈「几百个型号的询盘一时报不了价，待分配里只显示今天要处理的」，OpenSpec inquiry-sourcing-efficiency）。
-- 待分配任务可以延后到某一天：延后期间不进待分配列表、不参与分配；到期当天自动回来。状态不变，只加延后字段。
-- 回退见 sql/build/rollback/V1.5.29__sourcing_task_defer_rollback.sql
ALTER TABLE `sourcing_task`
    ADD COLUMN `defer_until`  date         NULL               COMMENT '延后到哪天（含当天回到待分配）；NULL 表示没有延后' AFTER `pool_entered_at`,
    ADD COLUMN `defer_reason` varchar(100) NOT NULL DEFAULT '' COMMENT '延后原因' AFTER `defer_until`,
    ADD COLUMN `defer_by`     bigint       NULL               COMMENT '延后操作人用户ID' AFTER `defer_reason`,
    ADD COLUMN `defer_time`   datetime     NULL               COMMENT '延后操作时间' AFTER `defer_by`,
    ADD INDEX `idx_sourcing_task_defer_until` (`defer_until`);
