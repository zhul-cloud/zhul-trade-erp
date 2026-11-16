-- 客户字段来源新增 5-AI 翻译：客户中文名称由 AI 按英文名称翻译时记此来源（openspec feedback-1116-batch1）
-- 只改注释，不改数据

ALTER TABLE `customer_field_source`
    MODIFY COLUMN `source` tinyint NOT NULL DEFAULT '1' COMMENT '来源（1-人工、2-背调报告、3-迁移、4-跟进记录、5-AI翻译）';

-- 回退：
-- UPDATE `customer_field_source` SET `source` = 1 WHERE `source` = 5;
-- ALTER TABLE `customer_field_source`
--     MODIFY COLUMN `source` tinyint NOT NULL DEFAULT '1' COMMENT '来源（1-人工、2-背调报告、3-迁移、4-跟进记录）';
