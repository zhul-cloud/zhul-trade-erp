-- ===========================
-- 客户询盘增加「总数量」：所有型号的数量直接相加（不区分单位），与型号数一起用于筛选、排序有价值的询盘
-- 待确认时为 AI 识别结果的合计，确认后为型号明细的合计
-- ===========================
ALTER TABLE `customer_inquiry`
    ADD COLUMN `total_quantity` int(11) NOT NULL DEFAULT 0 COMMENT '总数量（所有型号数量之和，不区分单位；待确认时为 AI 识别结果）' AFTER `total_item_count`,
    ADD KEY `idx_total_item_count` (`total_item_count`),
    ADD KEY `idx_total_quantity` (`total_quantity`);

-- 已确认：按型号明细汇总
UPDATE `customer_inquiry` ci
JOIN (
    SELECT `customer_inquiry_id`, SUM(`quantity`) AS qty
    FROM `inquiry_item`
    WHERE `deleted_at` IS NULL
    GROUP BY `customer_inquiry_id`
) i ON i.`customer_inquiry_id` = ci.`id`
SET ci.`total_quantity` = i.qty;

-- 待确认的 AI 解析结果：按解析输出汇总
UPDATE `customer_inquiry` ci
JOIN (
    SELECT t.`id` AS ai_task_id, SUM(COALESCE(j.qty, 0)) AS qty
    FROM `ai_task` t,
         JSON_TABLE(t.`output`, '$.groups[*].items[*]' COLUMNS (qty INT PATH '$.quantity' NULL ON EMPTY NULL ON ERROR)) j
    WHERE JSON_VALID(t.`output`)
    GROUP BY t.`id`
) a ON a.ai_task_id = ci.`ai_task_id`
SET ci.`total_quantity` = a.qty
WHERE ci.`status` = 3 AND ci.`parse_mode` = 1;
