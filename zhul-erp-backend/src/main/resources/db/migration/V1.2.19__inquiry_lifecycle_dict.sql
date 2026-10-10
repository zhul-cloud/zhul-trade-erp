-- ===========================
-- 型号生命周期改为字典管理：解析确认、采购核实、询价包共用
-- 码值 2（停产）在代码里有固定含义：可填替代型号、询价包标红；码值 3（待查）为默认值。可改名称、可新增选项，但不要改这两个码值的含义
-- 可重复执行：字典与字典项按编码判重
-- ===========================
INSERT INTO `dict_type` (`tenant_id`, `dict_type`, `dict_name`, `is_builtin`, `status`, `remark`, `create_by`, `update_by`)
SELECT 0, 'inquiry_lifecycle', '生命周期', 1, 1, '询盘型号的生产状态，字典值为整数码值；2-停产时可填替代型号，3-待查为默认值', 'sys', 'sys'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `dict_type` WHERE `dict_type` = 'inquiry_lifecycle' AND `tenant_id` = 0);

INSERT INTO `dict_item` (`tenant_id`, `dict_type_id`, `dict_type`, `item_code`, `item_name`, `item_value`, `sort_order`, `is_default`, `status`, `create_by`, `update_by`)
SELECT 0, t.id, t.dict_type, v.item_code, v.item_name, v.item_value, v.sort_order, v.is_default, 1, 'sys', 'sys'
FROM `dict_type` t
JOIN (
    SELECT 'ACTIVE' AS item_code, '在产' AS item_name, '1' AS item_value, 1 AS sort_order, 0 AS is_default
    UNION ALL SELECT 'DISCONTINUED', '停产', '2', 2, 0
    UNION ALL SELECT 'UNKNOWN', '待查', '3', 3, 1
) v
WHERE t.dict_type = 'inquiry_lifecycle' AND t.tenant_id = 0
  AND NOT EXISTS (SELECT 1 FROM `dict_item` i WHERE i.dict_type_id = t.id AND i.item_code = v.item_code);

ALTER TABLE `inquiry_item`
    MODIFY COLUMN `lifecycle` tinyint(2) NOT NULL DEFAULT 3 COMMENT '生命周期（字典 inquiry_lifecycle：1-在产、2-停产、3-待查）';
