-- audit BUS-095：先把型号报给客户、后分配（或撤回后重新分配）的询价任务，分配时没有重算，一直停在询价中。
-- 代码已在分配、认领、转派、追加采购后重算；这里把已经卡住的任务补成已回价，口径同 SourcingProgress.refreshTask：
-- 任务在询价中（2），且每个待询价（quote_status = 1）型号都已在当前版本的已发送、已成交、未成交、部分成交报价单里。
-- 2026-11-25 生产只读核对符合条件的只有 IQ20261117049A、IQ20261121014I 两个任务。
-- 版本号用 V1.5.61.1：排在 V1.5.62（UI 改造分支，尚未合入）之前，先上线不影响它之后按顺序执行。
UPDATE sourcing_task t
SET t.status = 3,
    t.completed_at = NOW(),
    t.update_time = NOW(),
    t.update_by = 'BUS-095'
WHERE t.status = 2
  AND t.deleted_at IS NULL
  AND EXISTS (SELECT 1 FROM inquiry_item i
              WHERE i.sourcing_task_id = t.id AND i.deleted_at IS NULL AND i.quote_status = 1)
  AND NOT EXISTS (
      SELECT 1 FROM inquiry_item i
      WHERE i.sourcing_task_id = t.id AND i.deleted_at IS NULL AND i.quote_status = 1
        AND NOT EXISTS (SELECT 1 FROM quotation_item qi
                        JOIN quotation q ON q.id = qi.quotation_id
                        WHERE qi.inquiry_item_id = i.id AND qi.is_current = 1 AND qi.deleted_at IS NULL
                          AND q.deleted_at IS NULL AND q.status IN (2, 3, 4, 6)));
