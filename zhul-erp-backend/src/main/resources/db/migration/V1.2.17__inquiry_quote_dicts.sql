-- ===========================
-- 货况、货期改为字典管理：采购回价、导入询价结果、历史询价共用
-- 可重复执行：字典与字典项按编码判重；货期只转换还不是码值的旧文本
-- ===========================
INSERT INTO `dict_type` (`tenant_id`, `dict_type`, `dict_name`, `is_builtin`, `status`, `remark`, `create_by`, `update_by`)
SELECT 0, v.dict_type, v.dict_name, 1, 1, v.remark, 'sys', 'sys'
FROM (
    SELECT 'inquiry_item_condition' AS dict_type, '货况' AS dict_name, '采购回价的货况，字典值为整数码值；1-全新原装用于默认选价' AS remark
    UNION ALL SELECT 'inquiry_lead_time', '货期', '采购回价的货期，字典值为整数码值'
) v
WHERE NOT EXISTS (SELECT 1 FROM `dict_type` t WHERE t.`dict_type` = v.dict_type AND t.`tenant_id` = 0);

INSERT INTO `dict_item` (`tenant_id`, `dict_type_id`, `dict_type`, `item_code`, `item_name`, `item_value`, `sort_order`, `is_default`, `status`, `create_by`, `update_by`)
SELECT 0, t.id, t.dict_type, v.item_code, v.item_name, v.item_value, v.sort_order, 0, 1, 'sys', 'sys'
FROM `dict_type` t
JOIN (
    SELECT 'inquiry_item_condition' AS dict_type, 'NEW' AS item_code, '全新原装' AS item_name, '1' AS item_value, 1 AS sort_order
    UNION ALL SELECT 'inquiry_item_condition', 'LIKE_NEW', '99新', '2', 2
    UNION ALL SELECT 'inquiry_item_condition', 'REFURBISHED', '翻新', '3', 3
    UNION ALL SELECT 'inquiry_item_condition', 'USED', '二手', '4', 4
    UNION ALL SELECT 'inquiry_item_condition', 'DISMANTLED', '拆机件', '5', 5
    UNION ALL SELECT 'inquiry_item_condition', 'DOMESTIC_ALT', '国产替代', '6', 6
    UNION ALL SELECT 'inquiry_item_condition', 'TO_CONFIRM', '待确认', '7', 7
    UNION ALL SELECT 'inquiry_lead_time', 'IN_STOCK', '现货', '1', 1
    UNION ALL SELECT 'inquiry_lead_time', 'DAYS_1_2', '1-2天', '2', 2
    UNION ALL SELECT 'inquiry_lead_time', 'DAYS_2_3', '2-3天', '3', 3
    UNION ALL SELECT 'inquiry_lead_time', 'DAYS_3_5', '3-5天', '4', 4
    UNION ALL SELECT 'inquiry_lead_time', 'DAYS_5_7', '5-7天', '5', 5
    UNION ALL SELECT 'inquiry_lead_time', 'WEEKS_1_2', '1-2周', '6', 6
    UNION ALL SELECT 'inquiry_lead_time', 'WEEKS_2_4', '2-4周', '7', 7
    UNION ALL SELECT 'inquiry_lead_time', 'WEEKS_4_8', '4-8周', '8', 8
    UNION ALL SELECT 'inquiry_lead_time', 'WEEKS_8_PLUS', '8周以上', '9', 9
) v ON v.dict_type = t.dict_type
WHERE t.tenant_id = 0
  AND NOT EXISTS (SELECT 1 FROM `dict_item` i WHERE i.dict_type_id = t.id AND i.item_code = v.item_code);

-- 货期旧文本 → 码值：能对上字典名称的换成码值，其余（含空）记为 0-未填
UPDATE `sourcing_quote` q
LEFT JOIN `dict_item` i ON i.`dict_type` = 'inquiry_lead_time' AND i.`tenant_id` = 0 AND i.`deleted_at` IS NULL
    AND i.`item_name` = TRIM(q.`lead_time`)
SET q.`lead_time` = COALESCE(i.`item_value`, '0'),
    q.`update_time` = CURRENT_TIMESTAMP
WHERE q.`lead_time` NOT REGEXP '^[0-9]+$';

ALTER TABLE `sourcing_quote`
    MODIFY COLUMN `lead_time` tinyint(2) NOT NULL DEFAULT 0 COMMENT '货期（0-未填，其余见字典 inquiry_lead_time：1-现货、2-1-2天、3-2-3天、4-3-5天、5-5-7天、6-1-2周、7-2-4周、8-4-8周、9-8周以上）',
    MODIFY COLUMN `item_condition` tinyint(2) NOT NULL DEFAULT 0 COMMENT '货况（0-未填，其余见字典 inquiry_item_condition：1-全新原装、2-99新、3-翻新、4-二手、5-拆机件、6-国产替代、7-待确认）';
