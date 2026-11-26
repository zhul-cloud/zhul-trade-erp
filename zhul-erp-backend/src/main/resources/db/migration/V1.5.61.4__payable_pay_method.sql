-- 应付显示对公 / 对私（openspec payable-payment-method）：采购订单约定付款方式，应付单冗余一份供列表筛选。

ALTER TABLE `purchase_order`
    ADD COLUMN `pay_method` tinyint(2) NOT NULL DEFAULT 2 COMMENT '付款方式（1-对公、2-对私）；开票默认对公，不开票与线上店铺默认对私' AFTER `invoice_required`;

ALTER TABLE `fin_payable`
    ADD COLUMN `pay_method` tinyint(2) NOT NULL DEFAULT 0 COMMENT '付款方式（0-未定、1-对公、2-对私）；采购订单取采购订单的付款方式，运费账单取货代默认收款账户类型' AFTER `payee_name`,
    ADD KEY `idx_pay_method` (`pay_method`);

-- 存量采购订单：供应商且开票的为对公，其余（不开票、线上店铺）为对私
UPDATE `purchase_order`
SET `pay_method` = CASE WHEN `channel` = 4 AND `invoice_required` = 1 THEN 1 ELSE 2 END;

-- 存量应付：采购订单的取采购订单
UPDATE `fin_payable` p
    JOIN `purchase_order` o ON o.`id` = p.`source_id`
SET p.`pay_method` = o.`pay_method`
WHERE p.`source_type` = 1;

-- 运费账单的取货代默认收款账户类型（没有默认账户的保持未定）
UPDATE `fin_payable` p
    JOIN `supplier_bank_account` a ON a.`supplier_id` = p.`supplier_id` AND a.`tenant_id` = p.`tenant_id`
        AND a.`is_default` = 1 AND a.`deleted_at` IS NULL
SET p.`pay_method` = a.`account_type`
WHERE p.`source_type` = 2;

-- 费用单的按费用单付款方式（1-公司对公、3-私对私）
UPDATE `fin_payable` p
    JOIN `fin_expense` e ON e.`id` = p.`source_id`
SET p.`pay_method` = CASE e.`method` WHEN 1 THEN 1 WHEN 3 THEN 2 ELSE 0 END
WHERE p.`source_type` = 3;
