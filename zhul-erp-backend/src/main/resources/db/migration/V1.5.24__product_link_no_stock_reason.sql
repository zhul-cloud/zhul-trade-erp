-- V1.5.24：2026-11-10 反馈二（openspec feedback-1110b-product-link-nostock-reason）。
-- 1. 手动创建的销售订单明细记下所选商品档案（只对新建的手动订单写入，已有数据保持为空）。
-- 2. 采购把型号标为无货时记下内部无货原因（字典「无货原因」的字典值），与报价单给客户的英文原因分开。
-- 字典部分可重复执行。
-- 回退：
--   ALTER TABLE `sales_order_item` DROP INDEX `idx_product_id`, DROP COLUMN `product_id`;
--   ALTER TABLE `sourcing_quote` DROP COLUMN `no_stock_reason`;
--   DELETE i FROM `dict_item` i JOIN `dict_type` t ON t.`id` = i.`dict_type_id`
--     WHERE t.`dict_type` = 'inquiry_no_stock_reason' AND t.`tenant_id` = 0;
--   DELETE FROM `dict_type` WHERE `dict_type` = 'inquiry_no_stock_reason' AND `tenant_id` = 0;

ALTER TABLE `sales_order_item`
    ADD COLUMN `product_id` bigint NULL COMMENT '商品档案ID（手动创建订单时从商品档案选择；转成的订单为空，经询盘型号追溯）' AFTER `inquiry_item_id`,
    ADD INDEX `idx_product_id` (`product_id`);

ALTER TABLE `sourcing_quote`
    ADD COLUMN `no_stock_reason` int NULL COMMENT '内部无货原因（字典 inquiry_no_stock_reason 的字典值，如 1-厂家停产；无货时在线录入必填，导入与历史数据为空）' AFTER `no_stock`;

INSERT INTO `dict_type` (`tenant_id`, `dict_type`, `dict_name`, `is_builtin`, `status`, `remark`, `create_by`, `update_by`)
SELECT 0, 'inquiry_no_stock_reason', '无货原因', 1, 1, '采购把型号标为无货时选择的内部原因，只在系统内显示；字典值为整数', 'sys', 'sys'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `dict_type` WHERE `dict_type` = 'inquiry_no_stock_reason' AND `tenant_id` = 0);

INSERT INTO `dict_item` (`tenant_id`, `dict_type_id`, `dict_type`, `item_code`, `item_name`, `item_value`, `sort_order`, `is_default`, `status`, `remark`, `create_by`, `update_by`)
SELECT 0, t.id, t.dict_type, v.item_code, v.item_name, v.item_value, v.sort_order, 0, 1, v.remark, 'sys', 'sys'
FROM `dict_type` t
JOIN (
    SELECT 'DISCONTINUED' AS item_code, '厂家停产' AS item_name, '1' AS item_value, 1 AS sort_order, '厂家或代理商确认已停产' AS remark
    UNION ALL SELECT 'OUT_OF_STOCK', '暂时缺货', '2', 2, '各渠道暂时没有现货，之后可能有'
    UNION ALL SELECT 'NO_SOURCE', '渠道没有货源', '3', 3, '常用渠道都找不到这个型号'
    UNION ALL SELECT 'LONG_LEAD_TIME', '交期太长', '4', 4, '能订到但交期超出客户可接受范围'
    UNION ALL SELECT 'OTHER', '其他', '5', 5, '其他原因，请在说明里写清楚'
) v
WHERE t.dict_type = 'inquiry_no_stock_reason' AND t.tenant_id = 0
  AND NOT EXISTS (SELECT 1 FROM `dict_item` i WHERE i.dict_type_id = t.id AND i.item_code = v.item_code);
