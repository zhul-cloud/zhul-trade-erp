-- ===========================
-- PI 有效期与关闭：有效期存在版本上（新建时开 PI 当天 + 60 天），PI 表头冗余当前有效版本的有效期供列表与工作台查询；
-- 客户最终没付款时业务员关闭 PI（状态 5-已关闭），可同时把来源报价单标为未成交，重新打开时按 lost_by_pi_id 恢复
-- 见 openspec/changes/add-pi-validity-close
-- ===========================

ALTER TABLE `proforma_invoice_version`
    ADD COLUMN `valid_until` date NULL COMMENT '有效期至（新建时为开 PI 当天 + 60 天，新版本沿用上一版本）' AFTER `remark`;

ALTER TABLE `proforma_invoice`
    MODIFY COLUMN `status` tinyint(2) NOT NULL DEFAULT 1 COMMENT '状态（1-草稿、2-已发送、3-已转订单、4-已作废、5-已关闭）',
    ADD COLUMN `valid_until`       date         NULL                COMMENT '有效期至（取当前有效版本，没有时取编辑中的版本）' AFTER `total_amount_cny`,
    ADD COLUMN `close_reason`      varchar(64)  NOT NULL DEFAULT '' COMMENT '关闭原因（字典 quotation_lost_reason 的 item_code）' AFTER `sent_at`,
    ADD COLUMN `close_reason_name` varchar(64)  NOT NULL DEFAULT '' COMMENT '关闭原因名称快照' AFTER `close_reason`,
    ADD COLUMN `close_note`        varchar(300) NOT NULL DEFAULT '' COMMENT '关闭说明（选「其他」时必填）' AFTER `close_reason_name`,
    ADD COLUMN `closed_at`         datetime     NULL                COMMENT '关闭时间' AFTER `close_note`,
    ADD COLUMN `closed_by`         bigint       NULL                COMMENT '关闭人用户ID，关联user_basic.id' AFTER `closed_at`,
    ADD KEY `idx_pi_valid_until` (`valid_until`);

ALTER TABLE `quotation`
    ADD COLUMN `lost_by_pi_id` bigint NULL COMMENT '因关闭哪张 PI 而标为未成交，关联proforma_invoice.id；重新打开该 PI 时据此恢复' AFTER `lost_note`,
    ADD KEY `idx_quotation_lost_by_pi` (`lost_by_pi_id`);

-- 存量：版本有效期按 PI 创建日期 + 60 天回填，表头取当前有效版本（没有时取编辑中的版本）
UPDATE `proforma_invoice_version` v
JOIN `proforma_invoice` p ON p.`id` = v.`pi_id`
SET v.`valid_until` = DATE_ADD(DATE(p.`create_time`), INTERVAL 60 DAY)
WHERE v.`valid_until` IS NULL;

UPDATE `proforma_invoice`
SET `valid_until` = DATE_ADD(DATE(`create_time`), INTERVAL 60 DAY)
WHERE `valid_until` IS NULL;
