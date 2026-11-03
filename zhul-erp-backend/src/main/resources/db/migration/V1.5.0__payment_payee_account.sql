-- 登记付款记录对方收款账户（OpenSpec payment-payee-account）：账户取自供应商资料的收款账户，
-- 付款单保存账户 ID 与快照文字（类型、开户行、户名、账号后四位），不存完整账号。
ALTER TABLE `fin_payment`
    ADD COLUMN `payee_account_id` bigint      NULL                COMMENT '对方收款账户（supplier_bank_account.id），未选为空' AFTER `payer_account`,
    ADD COLUMN `payee_account`    varchar(200) NOT NULL DEFAULT '' COMMENT '对方收款账户快照（如 对公 · 招商银行 · 户名 ****8901）' AFTER `payee_account_id`;

-- 回退：
-- ALTER TABLE `fin_payment` DROP COLUMN `payee_account`, DROP COLUMN `payee_account_id`;
