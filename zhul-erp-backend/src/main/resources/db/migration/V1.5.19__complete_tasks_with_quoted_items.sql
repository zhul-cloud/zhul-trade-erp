-- BUS-031：询价中的任务里，剩下的待询价型号已经报给了客户（业务员直接按无货或自定价格报出），
-- 采购既不能回价也不能退回，任务一直挂在询价中。新规则下这些型号算有结果，这里一次性把已经卡住的任务改为已回价。
-- 只处理「待询价的型号全部已报出、且至少有一个」的询价中任务；已报出 = 出现在已发送 / 成交 / 部分成交 / 未成交（2、3、4、6）的报价单当前版本里。
-- 可重复执行：执行后这些任务不再是询价中。回退：按 completed_at 为本次执行时间、status = 3 的任务改回 2 即可，型号和报价单不受影响。
UPDATE `sourcing_task` t
SET t.`status`       = 3,
    t.`completed_at` = NOW(),
    t.`update_time`  = NOW(),
    t.`update_by`    = 'sys'
WHERE t.`status` = 2
  AND t.`deleted_at` IS NULL
  AND EXISTS (
      SELECT 1 FROM `inquiry_item` i
      WHERE i.`sourcing_task_id` = t.`id` AND i.`deleted_at` IS NULL AND i.`quote_status` = 1)
  AND NOT EXISTS (
      SELECT 1 FROM `inquiry_item` i
      WHERE i.`sourcing_task_id` = t.`id` AND i.`deleted_at` IS NULL AND i.`quote_status` = 1
        AND NOT EXISTS (
            SELECT 1 FROM `quotation_item` qi
            JOIN `quotation` q ON q.`id` = qi.`quotation_id`
            WHERE qi.`inquiry_item_id` = i.`id` AND qi.`is_current` = 1 AND qi.`deleted_at` IS NULL
              AND q.`deleted_at` IS NULL AND q.`status` IN (2, 3, 4, 6)));
