-- 11-22 反馈第二批（openspec 1122-batch2）
-- 1. 应收管理（spec finance/accounts-receivable）：财务管理 → 应收管理，只读，排在收款管理之前；不建表，数据取有效销售订单与形式发票上的收款汇总
-- 2. 私对私付款渠道（spec finance/payment）：我方对私账户与付款单加付款渠道（银行卡 / 支付宝 / 微信）
-- 3. 供应商收款码（spec partner/supplier）：存为供应商附件的两个新类型，不加列

ALTER TABLE `tenant_bank_account`
    ADD COLUMN `pay_channel` tinyint NOT NULL DEFAULT 1 COMMENT '付款渠道（1-银行卡、2-支付宝、3-微信）；支付宝、微信只用于对私账户' AFTER `account_type`;

ALTER TABLE `fin_payment`
    ADD COLUMN `pay_channel` tinyint NOT NULL DEFAULT 1 COMMENT '付款渠道（1-银行卡、2-支付宝、3-微信）；支付宝、微信只用于私对私' AFTER `method`;

ALTER TABLE `supplier_attachment`
    MODIFY COLUMN `category` tinyint NOT NULL COMMENT '附件类型（1-营业执照、2-开户许可证、3-资质证书、4-合同、5-其他、6-支付宝收款码、7-微信收款码）；6、7 每种只保留一张';

INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
    SELECT 100132 AS id, 100083 AS pid, 'RS2100132' AS code, '应收管理' AS name, 2 AS type, 0 AS sort, 'account-book' AS li, '' AS lsi, '' AS di, '' AS dsi,
           '/finance/receivables' AS path, '' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.id);

-- 套餐：有收款管理的套餐加上应收管理
UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 100132)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`)
  AND JSON_CONTAINS(`menu_ids`, '100085')
  AND NOT JSON_CONTAINS(`menu_ids`, '100132');

-- 角色：有收款管理菜单的得到应收管理；内置管理员都有
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, 100132, 'RS2100132'
FROM `role_resource` x
WHERE x.`resource_id` = 100085
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = 100132);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT r.`code`, 100132, 'RS2100132'
FROM `role` r
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = 100132);

-- ---------------------------------------------------------------- 回退（手动执行）
-- 菜单：只删菜单与授权，不影响业务数据；套餐 menu_ids 里追加的 ID 留着无害。
-- 付款渠道：回退前用支付宝 / 微信登记的付款单会丢失渠道（付款单本身、金额、核销不受影响）；支付宝 / 微信账户回退后按银行卡显示。
-- 收款码：回退后 6、7 类附件留在表里，旧版本不显示也不会误删（旧版本保存供应商时会把它们软删，回退后编辑供应商前先确认）。
-- ALTER TABLE `tenant_bank_account` DROP COLUMN `pay_channel`;
-- ALTER TABLE `fin_payment` DROP COLUMN `pay_channel`;
-- DELETE FROM `role_resource` WHERE `resource_id` = 100132;
-- DELETE FROM `resource` WHERE `id` = 100132;
