-- 客户跟进（openspec change customer-followup）：取消手动赢单后补齐存量商机
-- 规则（spec crm/opportunity「收款到账自动赢单」）：客户任一 PI 已收金额 > 0，该客户进行中的商机即赢单。
-- 上线前已经有收款、但商机还在进行中的，一次性补成赢单：
--   1. 不在 S7 的，先补一条推进到 S7 的阶段记录；
--   2. 再补一条 S7 → 赢单的阶段记录；
--   3. 商机改为赢单，重新打开时回到 S7。
-- 存量报价单、PI、询盘的跟进对象不在这里生成，由 FollowupReconcileJob 在应用启动时生成（design D1）。

CREATE TEMPORARY TABLE `tmp_followup_auto_win` (
    `opportunity_id` bigint      NOT NULL,
    `tenant_id`      int(11)     NOT NULL,
    `from_stage`     varchar(16) NOT NULL,
    PRIMARY KEY (`opportunity_id`)
);

INSERT INTO `tmp_followup_auto_win` (`opportunity_id`, `tenant_id`, `from_stage`)
SELECT o.`id`, o.`tenant_id`, o.`stage_code`
FROM `opportunity` o
JOIN `opportunity_stage` s ON s.`tenant_id` = 0 AND s.`code` = o.`stage_code` AND s.`category` = 1
WHERE o.`deleted_at` IS NULL
  AND EXISTS (SELECT 1 FROM `proforma_invoice` p
              WHERE p.`tenant_id` = o.`tenant_id` AND p.`customer_id` = o.`customer_id`
                AND p.`deleted_at` IS NULL AND p.`received_amount` > 0);

INSERT INTO `opportunity_stage_log` (`tenant_id`, `opportunity_id`, `from_stage`, `to_stage`, `action`, `reason`, `note`, `create_by`, `update_by`)
SELECT t.`tenant_id`, t.`opportunity_id`, t.`from_stage`, 'S7', 2, 0, '上线补齐：已有收款，推进到 S7', 'sys', 'sys'
FROM `tmp_followup_auto_win` t
WHERE t.`from_stage` <> 'S7';

INSERT INTO `opportunity_stage_log` (`tenant_id`, `opportunity_id`, `from_stage`, `to_stage`, `action`, `reason`, `note`, `create_by`, `update_by`)
SELECT t.`tenant_id`, t.`opportunity_id`, 'S7', 'WON', 3, 0, '上线补齐：已有收款，自动赢单', 'sys', 'sys'
FROM `tmp_followup_auto_win` t;

UPDATE `opportunity` o
JOIN `tmp_followup_auto_win` t ON t.`opportunity_id` = o.`id`
SET o.`stage_code` = 'WON',
    o.`reopen_stage_code` = 'S7',
    o.`stage_changed_at` = NOW(),
    o.`close_reason` = 0,
    o.`close_note` = '',
    o.`closed_at` = NOW(),
    o.`reached_valid` = 1,
    o.`migrated` = 0,
    o.`update_time` = NOW(),
    o.`update_by` = 'sys';

DROP TEMPORARY TABLE `tmp_followup_auto_win`;

-- 回退：数据补齐不自动回退。需要撤销时，按阶段记录 note 找回本次补齐的商机：
-- 1. 用 note = '上线补齐：已有收款，自动赢单' 的记录找到商机，按同商机 note = '上线补齐：已有收款，推进到 S7' 记录的 from_stage
--    （没有这条记录的回到 S7）恢复 stage_code，并清空 closed_at、reopen_stage_code；
-- 2. 删除这两类阶段记录（阶段记录只增不改，回退属于例外，需人工确认）。
