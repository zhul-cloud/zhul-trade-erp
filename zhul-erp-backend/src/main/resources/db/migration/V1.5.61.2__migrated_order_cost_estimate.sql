-- audit BUS-094：两张迁移订单（SO20261019002、SO20261020002）旧系统没做成本核算、也还没采购，
-- 采购成本价为空时订单成本按 0 计、毛利 100%。用户决定（2026-11-25）：先按固定毛利 17% 估算成本，
-- 即采购成本价（CNY/个）= 单价 × 订单汇率 × 83%，HALF_UP 保留两位小数。
-- 按订单成本「还没下采购订单的数量按采购成本价计」的规则生效，之后下了采购订单就改按实际采购价。
-- 只填还没有采购成本价、没有成本核算快照的行；没有这两张订单的环境不改动任何数据。
UPDATE `sales_order_item` i
    JOIN `sales_order` o ON o.`id` = i.`so_id`
SET i.`cost_price`  = ROUND(i.`unit_price` * o.`exchange_rate` * 0.83, 2),
    i.`update_time` = NOW(),
    i.`update_by`   = 'BUS-094'
WHERE o.`so_no` IN ('SO20261019002', 'SO20261020002')
  AND o.`migrated` = 1
  AND o.`deleted_at` IS NULL
  AND i.`deleted_at` IS NULL
  AND i.`cost_price` IS NULL
  AND NOT EXISTS (SELECT 1 FROM `sales_order_cost_snapshot` s WHERE s.`so_id` = o.`id` AND s.`deleted_at` IS NULL);
