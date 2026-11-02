-- 售后处理时限（OpenSpec 变更 after-sales-sla）
-- 售后单按等级计算当前截止时间（首次响应 / 更新客户 / 手填时间）；跟进记录可标「已告知客户」。
-- 存量未结案售后单视为已首次响应、不填起算时间（只看手填的下次更新时间），下次告知客户后才按等级计时，避免上线即逾期。
-- 回退 SQL 在文件末尾的注释中。

ALTER TABLE `after_sales`
    ADD COLUMN `responded_at`    datetime   NULL                COMMENT '首次告知客户时间（首次响应），NULL表示还没响应' AFTER `next_update_at`,
    ADD COLUMN `last_contact_at` datetime   NULL                COMMENT '最近一次告知客户时间' AFTER `responded_at`,
    ADD COLUMN `sla_start_at`    datetime   NULL                COMMENT '更新客户间隔的起算时间（告知客户或改回处理中时更新）' AFTER `last_contact_at`,
    ADD COLUMN `due_at`          datetime   NULL                COMMENT '当前截止时间，NULL表示不提醒' AFTER `sla_start_at`,
    ADD COLUMN `due_type`        tinyint(2) NOT NULL DEFAULT 0  COMMENT '截止事项（0-无、1-首次响应、2-更新客户、3-手填时间）' AFTER `due_at`,
    ADD INDEX `idx_as_due` (`due_at`);

ALTER TABLE `after_sales_log`
    ADD COLUMN `customer_notified` tinyint(1) NOT NULL DEFAULT 0 COMMENT '已告知客户（0-否、1-是），仅跟进记录' AFTER `content`;

UPDATE `after_sales`
SET `responded_at` = `create_time`, `last_contact_at` = `create_time`,
    `due_at` = `next_update_at`, `due_type` = IF(`next_update_at` IS NULL, 0, 3)
WHERE `status` IN (1, 2) AND `deleted_at` IS NULL;

-- 回退：
-- ALTER TABLE `after_sales_log` DROP COLUMN `customer_notified`;
-- ALTER TABLE `after_sales` DROP INDEX `idx_as_due`, DROP COLUMN `due_type`, DROP COLUMN `due_at`, DROP COLUMN `sla_start_at`,
--     DROP COLUMN `last_contact_at`, DROP COLUMN `responded_at`;
