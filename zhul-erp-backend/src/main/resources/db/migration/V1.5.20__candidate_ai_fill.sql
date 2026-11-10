-- V1.5.20：商品候选批量 AI 解析品类、品牌（2026-11-09 反馈第 1 项，openspec candidate-batch-ai-fill）。
-- 记录候选的品类、品牌是否由 AI 写入，以及 AI 给出但品牌库匹配不上的品牌名（审核时新建品牌的参考）。
-- 回退：代码回退后字段不再被读取；需要删除时
--   ALTER TABLE `product_candidate` DROP COLUMN `ai_category`, DROP COLUMN `ai_brand`, DROP COLUMN `ai_brand_name`;
ALTER TABLE `product_candidate`
    ADD COLUMN `ai_category`   tinyint(2)  NOT NULL DEFAULT 0  COMMENT '品类是否 AI 建议（0-否、1-是）' AFTER `category_text`,
    ADD COLUMN `ai_brand`      tinyint(2)  NOT NULL DEFAULT 0  COMMENT '品牌 AI 解析结果（0-未解析、1-匹配到已有品牌、2-未识别）' AFTER `ai_category`,
    ADD COLUMN `ai_brand_name` varchar(64) NOT NULL DEFAULT '' COMMENT 'AI 给出的品牌名（未识别时供审核参考）' AFTER `ai_brand`;
