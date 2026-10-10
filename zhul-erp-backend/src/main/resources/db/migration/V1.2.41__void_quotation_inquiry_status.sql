-- 作废的报价单视为没报过（spec inquiry/inquiry-intake「客户询盘状态」）：
-- 之前询盘的报价单全部作废时会被置为「未成交」(9)，导致不能再报价。
-- 修复范围：状态为未成交、其型号所在的有效报价单里没有已发送(2)/已成交(3)/未成交(4)/部分成交(6)、但至少有一张已作废(5)的询盘；
-- 按回价进度回到可报价(6)（全部型号有价格或无货）或询价中(5)。被标为未成交的询盘不动。
UPDATE `customer_inquiry` ci
SET ci.`status` = CASE WHEN ci.`total_item_count` > 0 AND ci.`priced_item_count` >= ci.`total_item_count` THEN 6 ELSE 5 END,
    ci.`update_time` = NOW()
WHERE ci.`status` = 9
  AND ci.`deleted_at` IS NULL
  AND EXISTS (
      SELECT 1 FROM `quotation_item` qi
      JOIN `quotation` q ON q.`id` = qi.`quotation_id` AND q.`deleted_at` IS NULL
      WHERE qi.`customer_inquiry_id` = ci.`id` AND qi.`is_current` = 1 AND qi.`deleted_at` IS NULL AND q.`status` = 5)
  AND NOT EXISTS (
      SELECT 1 FROM `quotation_item` qi
      JOIN `quotation` q ON q.`id` = qi.`quotation_id` AND q.`deleted_at` IS NULL
      WHERE qi.`customer_inquiry_id` = ci.`id` AND qi.`is_current` = 1 AND qi.`deleted_at` IS NULL AND q.`status` IN (2, 3, 4, 6));
