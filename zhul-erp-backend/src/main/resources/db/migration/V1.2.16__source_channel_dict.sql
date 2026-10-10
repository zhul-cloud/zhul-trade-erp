-- ===========================
-- 来源渠道统一为字典管理：客户、商机、客户询盘共用一套码值
-- 可重复执行：字典已存在时跳过码值映射与插入（先映射再建字典，以字典是否存在判断是否已映射）
-- ===========================
-- 客户询盘原来源（1-WhatsApp、2-邮件、3-阿里国际站、4-微信、5-电话、6-其他）改为来源渠道码值：
-- 阿里国际站→1 阿里巴巴国际站；WhatsApp、微信→5 社交媒体；邮件、电话、其他→8 其他
UPDATE `customer_inquiry`
SET `source` = CASE `source` WHEN 3 THEN 1 WHEN 1 THEN 5 WHEN 4 THEN 5 ELSE 8 END,
    `update_time` = CURRENT_TIMESTAMP
WHERE `source` BETWEEN 1 AND 6
  AND NOT EXISTS (SELECT 1 FROM `dict_type` WHERE `dict_type` = 'crm_source_channel' AND `tenant_id` = 0);

INSERT INTO `dict_type` (`tenant_id`, `dict_type`, `dict_name`, `is_builtin`, `status`, `remark`, `create_by`, `update_by`)
SELECT 0, 'crm_source_channel', '来源渠道', 1, 1, '客户、商机、客户询盘共用的来源渠道，字典值为整数码值', 'sys', 'sys'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `dict_type` WHERE `dict_type` = 'crm_source_channel' AND `tenant_id` = 0);

INSERT INTO `dict_item` (`tenant_id`, `dict_type_id`, `dict_type`, `item_code`, `item_name`, `item_value`, `sort_order`, `is_default`, `status`, `create_by`, `update_by`)
SELECT 0, t.id, t.dict_type, v.item_code, v.item_name, v.item_value, v.sort_order, 0, 1, 'sys', 'sys'
FROM `dict_type` t
JOIN (
    SELECT 'ALIBABA' AS item_code, '阿里巴巴国际站' AS item_name, '1' AS item_value, 1 AS sort_order
    UNION ALL SELECT 'MADE_IN_CHINA', '中国制造网', '2', 2
    UNION ALL SELECT 'WEBSITE', '独立站', '3', 3
    UNION ALL SELECT 'EXHIBITION', '展会', '4', 4
    UNION ALL SELECT 'SOCIAL_MEDIA', '社交媒体', '5', 5
    UNION ALL SELECT 'REFERRAL', '老客户转介绍', '6', 6
    UNION ALL SELECT 'OUTBOUND', '主动开发', '7', 7
    UNION ALL SELECT 'OTHER', '其他', '8', 8
) v
WHERE t.dict_type = 'crm_source_channel' AND t.tenant_id = 0
  AND NOT EXISTS (SELECT 1 FROM `dict_item` i WHERE i.dict_type_id = t.id AND i.item_code = v.item_code);

ALTER TABLE `customer_inquiry`
    MODIFY COLUMN `source` tinyint(2) NOT NULL DEFAULT 8 COMMENT '来源渠道（字典 crm_source_channel：1-阿里巴巴国际站、2-中国制造网、3-独立站、4-展会、5-社交媒体、6-老客户转介绍、7-主动开发、8-其他）';
