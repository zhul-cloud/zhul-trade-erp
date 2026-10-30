-- V1.3.23：采购订单按现货 / 期货拆单。生成、追加草稿时按「采购员 + 采购对象 + 现货 / 期货」分组，单内型号一致。
ALTER TABLE `purchase_order`
    ADD COLUMN `stock_type` tinyint(2) NOT NULL DEFAULT 1 COMMENT '现货 / 期货（1-现货、2-期货），取自来源销售订单行' AFTER `shop_name`,
    ADD KEY `idx_po_stock_type` (`stock_type`);

-- 回填：单内型号的来源订单行都是期货的为期货；混有现货或没有来源订单行的按现货
UPDATE `purchase_order` po
SET po.`stock_type` = 2
WHERE EXISTS (SELECT 1 FROM `purchase_order_item` i
              JOIN `sales_order_item` s ON s.`id` = i.`so_item_id`
              WHERE i.`po_id` = po.`id` AND i.`deleted_at` IS NULL AND s.`stock_type` = 2)
  AND NOT EXISTS (SELECT 1 FROM `purchase_order_item` i
                  LEFT JOIN `sales_order_item` s ON s.`id` = i.`so_item_id`
                  WHERE i.`po_id` = po.`id` AND i.`deleted_at` IS NULL AND (s.`id` IS NULL OR s.`stock_type` <> 2));
