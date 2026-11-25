-- audit BUS-094：另外 3 张迁移订单同样没有成本核算快照、没有采购、没有采购成本价，成本按 0 计、毛利 100%。
-- 用户决定（2026-11-25）：同样按固定毛利 17% 估算成本，HALF_UP 保留两位小数。

-- 1) SO20260618001、SO20260626002：型号行的采购成本价（CNY/个）= 单价 × 订单汇率 × 83%，
--    按订单成本「还没下采购订单的数量按采购成本价计」生效，之后下了采购订单就改按实际采购价。
UPDATE `sales_order_item` i
    JOIN `sales_order` o ON o.`id` = i.`so_id`
SET i.`cost_price`  = ROUND(i.`unit_price` * o.`exchange_rate` * 0.83, 2),
    i.`update_time` = NOW(),
    i.`update_by`   = 'BUS-094'
WHERE o.`so_no` IN ('SO20260618001', 'SO20260626002')
  AND o.`migrated` = 1
  AND o.`deleted_at` IS NULL
  AND i.`deleted_at` IS NULL
  AND i.`cost_price` IS NULL
  AND NOT EXISTS (SELECT 1 FROM `sales_order_cost_snapshot` s WHERE s.`so_id` = o.`id` AND s.`deleted_at` IS NULL);

-- 2) SO20250707001 没有型号行，没法填采购成本价：补一条成本核算快照，收入 = 订单合计折人民币，
--    成本 = 收入 × 83%，来源注明为估算。迁移订单按快照计收入与成本（spec finance/order-cost「迁移订单按历史核算快照」）。
INSERT INTO `sales_order_cost_snapshot`
    (`tenant_id`, `so_id`, `currency_code`, `revenue`, `refund`, `purchase_amount`, `freight`, `surcharge`, `service_fee`,
     `cost`, `gross_profit`, `margin_rate`, `settled`, `accounting_month`, `source_codes`, `create_by`, `update_by`)
SELECT o.`tenant_id`, o.`id`, 'CNY', o.`total_amount_cny`, 0, ROUND(o.`total_amount_cny` * 0.83, 2), 0, 0, 0,
       ROUND(o.`total_amount_cny` * 0.83, 2), o.`total_amount_cny` - ROUND(o.`total_amount_cny` * 0.83, 2), 17.00, 1,
       DATE_FORMAT(o.`sales_date`, '%Y-%m'), '估算 毛利17% BUS-094', 'BUS-094', 'BUS-094'
FROM `sales_order` o
WHERE o.`so_no` = 'SO20250707001'
  AND o.`migrated` = 1
  AND o.`deleted_at` IS NULL
  AND NOT EXISTS (SELECT 1 FROM `sales_order_item` i WHERE i.`so_id` = o.`id` AND i.`deleted_at` IS NULL)
  AND NOT EXISTS (SELECT 1 FROM `sales_order_cost_snapshot` s WHERE s.`so_id` = o.`id` AND s.`deleted_at` IS NULL);
