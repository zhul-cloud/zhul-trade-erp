-- V1.5.23：2026-11-10 反馈（openspec feedback-1110-fee-order-contact-ap）。
-- 1. 销售订单来源增加「4-费用订单」（只改字段注释）。
-- 2. 应付单增加「历史迁移视为已付」标记：历史迁移、下单日期在 2026-11-01 之前、仍未付或部分付款且没有任何付款核销的
--    采购单应付，迁移前已线下付清。标记后已付补满、状态已付清、付款计划各期已付清；以后重算时已付跟着应付走。
--    不生成付款记录。生产执行前核对为 609 张（全部人民币，合计 ¥4,564,623.02）。
-- 回退（标记的行都是本迁移改的，此前已付均为 0）：
--   UPDATE `fin_payable_installment` i JOIN `fin_payable` p ON p.`id` = i.`payable_id`
--      SET i.`paid_amount` = 0, i.`status` = 1 WHERE p.`settled_by_migration` = 1;
--   UPDATE `fin_payable` SET `paid_amount` = 0, `paid_cny` = 0, `status` = 1 WHERE `settled_by_migration` = 1;
--   （下一期到期日由下次应付同步重算，或按付款计划手工回填）
--   ALTER TABLE `fin_payable` DROP COLUMN `settled_by_migration`;

ALTER TABLE `sales_order`
    MODIFY COLUMN `source` tinyint(2) NOT NULL DEFAULT 1 COMMENT '来源（1-PI 转成、2-手动创建、3-售后补发、4-费用订单）';

ALTER TABLE `fin_payable`
    ADD COLUMN `settled_by_migration` tinyint(2) NOT NULL DEFAULT 0
        COMMENT '历史迁移视为已付（0-否、1-是）：迁移前已线下付清，没有付款记录' AFTER `next_due_date`;

UPDATE `fin_payable` p
    JOIN `purchase_order` po ON po.`id` = p.`source_id` AND po.`tenant_id` = p.`tenant_id`
SET p.`settled_by_migration` = 1,
    p.`paid_amount`          = p.`amount`,
    p.`paid_cny`             = p.`amount_cny`,
    p.`credit_amount`        = 0,
    p.`status`               = 3,
    p.`next_due_date`        = NULL,
    p.`update_time`          = NOW(),
    p.`update_by`            = 'migration'
WHERE p.`source_type` = 1
  AND p.`deleted_at` IS NULL
  AND p.`status` IN (1, 2)
  AND po.`migrated` = 1
  AND po.`order_date` < '2026-11-01'
  AND NOT EXISTS (SELECT 1 FROM `fin_payment_allocation` a
                  WHERE a.`deleted_at` IS NULL AND (a.`payable_id` = p.`id` OR a.`credit_payable_id` = p.`id`));

UPDATE `fin_payable_installment` i
    JOIN `fin_payable` p ON p.`id` = i.`payable_id`
SET i.`paid_amount` = i.`amount`,
    i.`status`      = 3,
    i.`update_time` = NOW(),
    i.`update_by`   = 'migration'
WHERE p.`settled_by_migration` = 1
  AND i.`deleted_at` IS NULL;
