-- V1.5.45：单据型号以真实型号为准、描述带到采购侧（openspec actual-model-and-description）
-- 1. 询盘型号记录真实型号的最后修改人与时间（业务员、采购都能改，后改的覆盖先改的）。
-- 2. 报价行、PI 行、销售订单行记录客户型号（询盘确认的型号）；为空表示与型号相同，存量行不回填。
-- 3. 采购需求、采购订单行增加描述（中文，来自销售订单型号行），存量按来源订单行回填。

ALTER TABLE `inquiry_item`
    ADD COLUMN `actual_model_by` varchar(32) NOT NULL DEFAULT '' COMMENT '真实型号最后修改人（清空真实型号时一并清空）' AFTER `actual_model_key`,
    ADD COLUMN `actual_model_at` datetime    NULL                COMMENT '真实型号最后修改时间，NULL表示没有真实型号' AFTER `actual_model_by`;

ALTER TABLE `quotation_item`
    ADD COLUMN `customer_model` varchar(128) NOT NULL DEFAULT '' COMMENT '客户型号（询盘确认的型号）；为空或与型号相同时不显示' AFTER `model`;

ALTER TABLE `proforma_invoice_item`
    ADD COLUMN `customer_model` varchar(128) NOT NULL DEFAULT '' COMMENT '客户型号（询盘确认的型号）；为空或与型号相同时不显示' AFTER `model`;

ALTER TABLE `sales_order_item`
    ADD COLUMN `customer_model` varchar(128) NOT NULL DEFAULT '' COMMENT '客户型号（询盘确认的型号）；为空或与型号相同时不显示' AFTER `model`;

ALTER TABLE `purchase_requirement`
    ADD COLUMN `description` varchar(300) NOT NULL DEFAULT '' COMMENT '描述（中文，来自销售订单型号行）' AFTER `category`;

ALTER TABLE `purchase_order_item`
    ADD COLUMN `description` varchar(300) NOT NULL DEFAULT '' COMMENT '描述（中文，来自采购需求）' AFTER `brand`;

-- 存量回填：同一租户内按来源订单型号行取描述
UPDATE `purchase_requirement` r
    JOIN `sales_order_item` s ON s.`id` = r.`so_item_id` AND s.`tenant_id` = r.`tenant_id`
SET r.`description` = s.`description`
WHERE s.`description` <> '';

UPDATE `purchase_order_item` p
    JOIN `sales_order_item` s ON s.`id` = p.`so_item_id` AND s.`tenant_id` = p.`tenant_id`
SET p.`description` = s.`description`
WHERE s.`description` <> '';

-- 回退：
-- ALTER TABLE `inquiry_item` DROP COLUMN `actual_model_by`, DROP COLUMN `actual_model_at`;
-- ALTER TABLE `quotation_item` DROP COLUMN `customer_model`;
-- ALTER TABLE `proforma_invoice_item` DROP COLUMN `customer_model`;
-- ALTER TABLE `sales_order_item` DROP COLUMN `customer_model`;
-- ALTER TABLE `purchase_requirement` DROP COLUMN `description`;
-- ALTER TABLE `purchase_order_item` DROP COLUMN `description`;
