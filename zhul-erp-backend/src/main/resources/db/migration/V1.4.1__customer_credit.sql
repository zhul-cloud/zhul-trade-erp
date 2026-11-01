-- 客户余额（OpenSpec 变更 customer-credit）
-- 余额流水与先进先出消耗、PI 与订单费用行的代收款、PI 版本的余额抵扣、退款单类型、系统生成的汇兑损益；
-- 菜单「财务管理 → 余额管理」与按钮「调整客户余额」。
-- 回退：见 openspec/changes/customer-credit/design.md 的 Migration Plan，回退 SQL 在文件末尾的注释中。

-- ---------------------------------------------------------------- 余额流水

CREATE TABLE IF NOT EXISTS `fin_customer_credit_entry` (
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `customer_id`      bigint        NOT NULL            COMMENT '客户ID',
    `currency_code`    varchar(8)    NOT NULL            COMMENT '币种',
    `business_unit_id` int(11)       NULL                COMMENT '所属事业部（department.id；NULL-公司没有设置事业部或公共）',
    `entry_type`       tinyint(2)    NOT NULL            COMMENT '类型（1-售后退款转入、2-代收转入、3-多收转入、4-抵扣占用、5-抵扣释放、6-提现、7-手工调整、8-冲销）',
    `amount`           decimal(18,2) NOT NULL            COMMENT '原币金额（转入为正、转出为负）',
    `amount_cny`       decimal(18,2) NOT NULL            COMMENT '锁定的人民币价值（与金额同号）',
    `lot_time`         datetime      NOT NULL            COMMENT '先进先出排序时间：转入为发生时间，释放沿用被释放转入的时间',
    `source_type`      varchar(16)   NOT NULL DEFAULT '' COMMENT '来源（REFUND-退款单、PI、SO-手动订单、MANUAL-手工调整）',
    `source_id`        bigint        NULL                COMMENT '来源单据ID',
    `source_no`        varchar(32)   NOT NULL DEFAULT '' COMMENT '来源单据编号',
    `related_entry_id` bigint        NULL                COMMENT '释放、冲销对应的流水ID',
    `remark`           varchar(500)  NOT NULL DEFAULT '' COMMENT '说明',
    `operator_id`      bigint        NULL                COMMENT '经办人',
    `deleted_at`       datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_credit_account` (`tenant_id`, `customer_id`, `currency_code`, `business_unit_id`),
    KEY `idx_credit_customer` (`customer_id`),
    KEY `idx_credit_source` (`source_type`, `source_id`),
    KEY `idx_credit_related` (`related_entry_id`),
    KEY `idx_credit_create` (`create_time`),
    KEY `idx_credit_deleted` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户余额流水';

CREATE TABLE IF NOT EXISTS `fin_customer_credit_consumption` (
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `out_entry_id`     bigint        NOT NULL            COMMENT '转出流水ID（抵扣占用、提现、负数调整、冲销）',
    `in_entry_id`      bigint        NOT NULL            COMMENT '被消耗的转入流水ID',
    `amount`           decimal(18,2) NOT NULL            COMMENT '消耗的原币金额（正数）',
    `amount_cny`       decimal(18,2) NOT NULL            COMMENT '消耗的人民币价值（正数）',
    `released`         tinyint(2)    NOT NULL DEFAULT 0  COMMENT '已释放（0-否、1-是）',
    `deleted_at`       datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_consume_tenant` (`tenant_id`),
    KEY `idx_consume_out` (`out_entry_id`),
    KEY `idx_consume_in` (`in_entry_id`),
    KEY `idx_consume_create` (`create_time`),
    KEY `idx_consume_deleted` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户余额先进先出消耗明细';

-- ---------------------------------------------------------------- 代收款与余额抵扣

ALTER TABLE `proforma_invoice_fee`
    ADD COLUMN `fee_type` tinyint(2) NOT NULL DEFAULT 1 COMMENT '费用类型（1-普通费用、2-代收款：客户请我们多做、收到后转回给客户，不计入销售额）' AFTER `fee_name`;

ALTER TABLE `sales_order_fee`
    ADD COLUMN `fee_type` tinyint(2) NOT NULL DEFAULT 1 COMMENT '费用类型（1-普通费用、2-代收款）' AFTER `fee_name`;

ALTER TABLE `proforma_invoice_version`
    ADD COLUMN `credit_amount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '抵扣客户余额（原币，导出为 Less: Credit）' AFTER `total_amount_cny`;

ALTER TABLE `payment_receipt`
    ADD COLUMN `credit_entry_id` bigint NULL COMMENT '余额抵扣对应的「抵扣占用」流水ID（付款方式 CREDIT）',
    ADD INDEX `idx_receipt_credit_entry` (`credit_entry_id`);

-- ---------------------------------------------------------------- 退款单类型与去向

ALTER TABLE `fin_customer_refund`
    ADD COLUMN `refund_type` tinyint(2) NOT NULL DEFAULT 1 COMMENT '类型（1-售后退款：冲减订单收入、计入经营统计；2-余额提现：不挂订单、不影响利润）' AFTER `refund_no`,
    MODIFY COLUMN `so_id` bigint NULL COMMENT '销售订单ID（余额提现为空）',
    ADD COLUMN `customer_id` bigint NULL COMMENT '客户ID' AFTER `so_id`,
    ADD COLUMN `business_unit_id` int(11) NULL COMMENT '所属事业部（售后退款取订单的事业部，余额提现取余额的事业部）' AFTER `customer_id`,
    MODIFY COLUMN `method` tinyint(2) NOT NULL DEFAULT 1 COMMENT '去向（1-原路退回平台、2-银行汇款、3-转客户余额、4-其他转出方式，如支付宝、微信）',
    ADD COLUMN `method_name` varchar(32) NOT NULL DEFAULT '' COMMENT '转出方式名称（去向为 4 时填写，如支付宝）' AFTER `method`,
    ADD COLUMN `payee_account` varchar(128) NOT NULL DEFAULT '' COMMENT '收款账户说明（余额提现）' AFTER `method_name`,
    ADD COLUMN `credit_entry_id` bigint NULL COMMENT '对应的余额流水ID（转客户余额的转入、余额提现的提现）',
    ADD INDEX `idx_refund_type` (`refund_type`),
    ADD INDEX `idx_refund_customer` (`customer_id`),
    ADD INDEX `idx_refund_bu` (`business_unit_id`);

UPDATE `fin_customer_refund` f
JOIN `sales_order` s ON s.`id` = f.`so_id`
SET f.`customer_id` = s.`customer_id`, f.`business_unit_id` = s.`business_unit_id`
WHERE f.`customer_id` IS NULL;

-- ---------------------------------------------------------------- 系统生成的其他收入（汇兑损益）

ALTER TABLE `fin_other_income`
    MODIFY COLUMN `amount` decimal(18,2) NOT NULL COMMENT '金额（人民币；系统生成的汇兑损益可为负数）',
    ADD COLUMN `source_type` varchar(16) NOT NULL DEFAULT '' COMMENT '来源（空-手动登记、REFUND-余额提现自动生成的汇兑损益）',
    ADD COLUMN `source_id` bigint NULL COMMENT '来源单据ID',
    ADD INDEX `idx_income_source` (`source_type`, `source_id`);

INSERT INTO `dict_item` (`tenant_id`, `dict_type_id`, `dict_type`, `item_code`, `item_name`, `item_name_en`, `item_value`, `sort_order`, `is_default`, `status`, `remark`, `create_by`, `update_by`)
SELECT 0, t.`id`, t.`dict_type`, 'FX_GAIN', '汇兑损益', 'FX Gain/Loss', 'FX_GAIN', 99, 0, 1, '系统生成：余额转给客户时，锁定的人民币价值与实际转出人民币的差额；不能手动登记', 'sys', 'sys'
FROM `dict_type` t
WHERE t.`dict_type` = 'other_income_category' AND t.`tenant_id` = 0
  AND NOT EXISTS (SELECT 1 FROM `dict_item` i WHERE i.`dict_type_id` = t.`id` AND i.`item_code` = 'FX_GAIN');

-- ---------------------------------------------------------------- 菜单与按钮

INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
              SELECT 100119 AS id, 100083 AS pid, 'RS2100119' AS code, '余额管理' AS name, 2 AS type, 3 AS sort, 'wallet' AS li, '' AS lsi, '' AS di, '' AS dsi,
                     '/finance/credits' AS path, '' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
    UNION ALL SELECT 110220, 100119, 'RS3110220', '调整客户余额', 3, 1, '', '', '', '', '', 'finance:credit:adjust', 1, '', 'sys', 'sys'
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.id);

-- 财务管理排序：收款管理、退款管理、余额管理、结汇管理、应付管理、付款管理、报销管理、费用管理、其他收入（system/navigation）
UPDATE `resource` SET `sort` = CASE `id`
    WHEN 100085 THEN 1 WHEN 100110 THEN 2 WHEN 100119 THEN 3 WHEN 100107 THEN 4 WHEN 100103 THEN 5
    WHEN 100104 THEN 6 WHEN 100105 THEN 7 WHEN 100106 THEN 8 WHEN 100111 THEN 9 END,
    `update_time` = NOW()
WHERE `id` IN (100085, 100110, 100119, 100107, 100103, 100104, 100105, 100106, 100111);

-- 套餐：含退款管理的补余额管理与调整按钮
UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(`menu_ids`, '$', 100119), '$', 110220)
WHERE JSON_CONTAINS(`menu_ids`, '100110') AND NOT JSON_CONTAINS(`menu_ids`, '100119');

-- 角色：有退款管理菜单的得到余额管理；有「退款单维护」按钮的得到「调整客户余额」；租户管理员全部
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, 100119, 'RS2100119'
FROM `role_resource` x
WHERE x.`resource_id` = 100110
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = 100119);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, 110220, 'RS3110220'
FROM `role_resource` x
WHERE x.`resource_id` = 110209
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = 110220);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` IN (100119, 110220)
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);

-- ---------------------------------------------------------------- 回退（手动执行，执行前确认余额流水已导出留档）
-- UPDATE `fin_customer_refund` SET `status` = 2, `void_reason` = '回退客户余额' WHERE `refund_type` = 2 AND `status` = 1;
-- UPDATE `fin_other_income` SET `status` = 2, `void_reason` = '回退客户余额' WHERE `source_type` = 'REFUND' AND `status` = 1;
-- UPDATE `payment_receipt` SET `status` = 2, `void_reason` = '回退客户余额' WHERE `payment_method` = 'CREDIT' AND `status` = 1;
-- DELETE FROM `role_resource` WHERE `resource_id` IN (100119, 110220);
-- DELETE FROM `resource` WHERE `id` IN (100119, 110220);
-- DELETE FROM `dict_item` WHERE `dict_type` = 'other_income_category' AND `item_code` = 'FX_GAIN';
-- ALTER TABLE `fin_other_income` DROP INDEX `idx_income_source`, DROP COLUMN `source_type`, DROP COLUMN `source_id`;
-- ALTER TABLE `fin_customer_refund` DROP INDEX `idx_refund_type`, DROP INDEX `idx_refund_customer`, DROP INDEX `idx_refund_bu`,
--     DROP COLUMN `refund_type`, DROP COLUMN `customer_id`, DROP COLUMN `business_unit_id`, DROP COLUMN `method_name`,
--     DROP COLUMN `payee_account`, DROP COLUMN `credit_entry_id`;
-- ALTER TABLE `payment_receipt` DROP INDEX `idx_receipt_credit_entry`, DROP COLUMN `credit_entry_id`;
-- ALTER TABLE `proforma_invoice_version` DROP COLUMN `credit_amount`;
-- ALTER TABLE `sales_order_fee` DROP COLUMN `fee_type`;
-- ALTER TABLE `proforma_invoice_fee` DROP COLUMN `fee_type`;
-- DROP TABLE `fin_customer_credit_consumption`;
-- DROP TABLE `fin_customer_credit_entry`;
-- （fin_customer_refund.so_id 保持可空：作废的余额提现退款单没有订单，旧版本读取时按空订单处理）
