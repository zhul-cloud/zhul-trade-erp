-- ===========================
-- 采购成本价由采购侧决定：
-- 1. 询价记录增加「推荐报价」：每位采购在同一型号的报价里标一条推荐
-- 2. 型号明细增加「成本价由采购负责人指定」：未指定时按推荐报价自动取（全新原装中最低，其次最低价）
-- 3. 渠道为「供应商」时关联供应商主数据（supplier_id 列已在 V1.2.15 建好，这里只改由服务端校验并填写）
-- ===========================
ALTER TABLE `sourcing_quote`
    ADD COLUMN `recommended` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否为该采购对该型号的推荐报价（0-否、1-是）' AFTER `note`;

ALTER TABLE `inquiry_item`
    ADD COLUMN `cost_manual` tinyint(1) NOT NULL DEFAULT 0 COMMENT '采购成本价是否由采购负责人手动指定（0-按推荐报价自动取、1-手动指定）' AFTER `selected_quote_id`;

-- 已有数据：当前选定的询价记录视为推荐报价
UPDATE `sourcing_quote` q
JOIN `inquiry_item` i ON i.`selected_quote_id` = q.`id`
SET q.`recommended` = 1
WHERE q.`deleted_at` IS NULL;
