-- V1.5.51：采购订单已付款后取消的收尾（openspec po-cancel-refund）
-- 取消时记录店铺 / 供应商是否退款；公司对公、私对私付款在选「会退款」后，应付单标记待退款，欠款冲到 0 时自动去掉。
-- 存量数据不回填：历史取消的采购订单视为未选，历史欠款不标待退款。

ALTER TABLE `purchase_order`
    ADD COLUMN `cancel_refund` tinyint(2) NOT NULL DEFAULT 0 COMMENT '取消时是否退款（0-无付款或未选、1-会退款、2-不退款）' AFTER `cancelled_at`;

ALTER TABLE `fin_payable`
    ADD COLUMN `refund_expected_at` datetime NULL COMMENT '待退款标记时间：采购订单取消选「会退款」且有公司付款时写入，欠款为 0 时清空；NULL 表示不是待退款' AFTER `credit_amount`,
    ADD INDEX `idx_fin_payable_refund_expected` (`refund_expected_at`);

-- 回退：见 sql/build/rollback/V1.5.51__po_cancel_refund_rollback.sql
