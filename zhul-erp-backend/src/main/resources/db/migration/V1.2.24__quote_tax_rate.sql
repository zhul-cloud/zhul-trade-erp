-- ===========================
-- 含税报价：采购可标「含税」并选税率，系统换算不含税价用于比价与采购成本价
-- unit_price 存采购填的原价（含税时为含税价）；unit_price_cny 存不含税本位币价 = 含税价 ÷ (1 + 税率)，两位小数 HALF_UP
-- 可重复执行
-- ===========================
INSERT INTO `dict_type` (`tenant_id`, `dict_type`, `dict_name`, `is_builtin`, `status`, `remark`, `create_by`, `update_by`)
SELECT 0, 'inquiry_tax_rate', '询价税率', 1, 1, '采购含税报价的增值税税率，字典值为百分比整数（13 表示 13%）；13 为默认值', 'sys', 'sys'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `dict_type` WHERE `dict_type` = 'inquiry_tax_rate' AND `tenant_id` = 0);

INSERT INTO `dict_item` (`tenant_id`, `dict_type_id`, `dict_type`, `item_code`, `item_name`, `item_value`, `sort_order`, `is_default`, `status`, `remark`, `create_by`, `update_by`)
SELECT 0, t.id, t.dict_type, v.item_code, v.item_name, v.item_value, v.sort_order, v.is_default, 1, v.remark, 'sys', 'sys'
FROM `dict_type` t
JOIN (
    SELECT 'VAT_13' AS item_code, '13%' AS item_name, '13' AS item_value, 1 AS sort_order, 1 AS is_default, '一般纳税人货物销售，增值税专票' AS remark
    UNION ALL SELECT 'VAT_3', '3%', '3', 2, 0, '小规模纳税人'
    UNION ALL SELECT 'VAT_1', '1%', '1', 3, 0, '小规模纳税人减按 1%'
) v
WHERE t.dict_type = 'inquiry_tax_rate' AND t.tenant_id = 0
  AND NOT EXISTS (SELECT 1 FROM `dict_item` i WHERE i.dict_type_id = t.id AND i.item_code = v.item_code);

ALTER TABLE `sourcing_quote`
    ADD COLUMN `tax_rate` decimal(5,2) NOT NULL DEFAULT 0.00 COMMENT '税率（百分比，含税时有值，如 13.00；不含税为 0）' AFTER `tax_included`,
    MODIFY COLUMN `unit_price_cny` decimal(18,2) NULL COMMENT '不含税本位币单价（含税时 = 含税价 ÷ (1 + 税率)，两位小数 HALF_UP），比价与采购成本价按它';

-- 旧数据里标了含税的按 13% 换算
UPDATE `sourcing_quote`
SET `tax_rate` = 13.00,
    `unit_price_cny` = ROUND(`unit_price` / 1.13, 2),
    `update_time` = CURRENT_TIMESTAMP
WHERE `tax_included` = 1 AND `tax_rate` = 0 AND `unit_price` IS NOT NULL;
