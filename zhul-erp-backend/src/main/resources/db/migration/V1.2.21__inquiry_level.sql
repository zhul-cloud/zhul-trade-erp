-- ===========================
-- 客户询盘增加「询盘等级」S/A/B/C：业务员录入时判断（默认 B），采购按等级决定先处理哪个询盘
-- 码值越小等级越高，任务排序按码值升序；可在字典管理中改名，不要调整码值顺序
-- 可重复执行
-- ===========================
INSERT INTO `dict_type` (`tenant_id`, `dict_type`, `dict_name`, `is_builtin`, `status`, `remark`, `create_by`, `update_by`)
SELECT 0, 'inquiry_level', '询盘等级', 1, 1, '客户询盘等级，字典值为整数码值，越小等级越高（用于采购任务排序）；3-B 为默认值', 'sys', 'sys'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `dict_type` WHERE `dict_type` = 'inquiry_level' AND `tenant_id` = 0);

INSERT INTO `dict_item` (`tenant_id`, `dict_type_id`, `dict_type`, `item_code`, `item_name`, `item_value`, `sort_order`, `is_default`, `status`, `remark`, `create_by`, `update_by`)
SELECT 0, t.id, t.dict_type, v.item_code, v.item_name, v.item_value, v.sort_order, v.is_default, 1, v.remark, 'sys', 'sys'
FROM `dict_type` t
JOIN (
    SELECT 'S' AS item_code, 'S' AS item_name, '1' AS item_value, 1 AS sort_order, 0 AS is_default, '战略级：长期稳定合作的重点客户或大单，最优先' AS remark
    UNION ALL SELECT 'A', 'A', '2', 2, 0, '重要：成交可能性高或金额较大'
    UNION ALL SELECT 'B', 'B', '3', 3, 1, '常规询盘'
    UNION ALL SELECT 'C', 'C', '4', 4, 0, '低优先：试探性询价、成交可能性低'
) v
WHERE t.dict_type = 'inquiry_level' AND t.tenant_id = 0
  AND NOT EXISTS (SELECT 1 FROM `dict_item` i WHERE i.dict_type_id = t.id AND i.item_code = v.item_code);

ALTER TABLE `customer_inquiry`
    ADD COLUMN `level` tinyint(2) NOT NULL DEFAULT 3 COMMENT '询盘等级（字典 inquiry_level：1-S、2-A、3-B、4-C，越小越优先）' AFTER `urgent`,
    ADD KEY `idx_level` (`level`);
