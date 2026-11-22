-- 结汇单追加结汇手续费（外币，银行从结汇金额里扣）：
-- 汇率 = 实际入账人民币 ÷ (结汇金额 − 手续费)；手续费人民币 = 手续费 × 汇率，四舍五入（HALF_UP）到 2 位；
-- 手续费按每笔收款分摊到的结汇金额比例分到收款，计入对应订单的收款手续费（成本）。已有结汇单手续费为 0，金额不变。
ALTER TABLE `fin_fx_settlement`
    ADD COLUMN `fee_amount`     DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '结汇手续费（原币），从结汇金额里扣' AFTER `exchange_rate`,
    ADD COLUMN `fee_amount_cny` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '结汇手续费折人民币 = 手续费 × 汇率' AFTER `fee_amount`;

ALTER TABLE `fin_fx_settlement_allocation`
    ADD COLUMN `fee_amount_cny` DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '分到这笔收款的结汇手续费（人民币）' AFTER `amount_cny`;

-- 回退（手续费不为 0 的结汇单须先作废重开，否则汇率与分摊人民币含手续费）：
-- ALTER TABLE `fin_fx_settlement_allocation` DROP COLUMN `fee_amount_cny`;
-- ALTER TABLE `fin_fx_settlement` DROP COLUMN `fee_amount_cny`, DROP COLUMN `fee_amount`;
