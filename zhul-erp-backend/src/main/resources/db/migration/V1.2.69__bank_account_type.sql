-- 收款账户类型（openspec bank-account-type）：对公 / 对私，私对私付款从对私账户里选；已有账户为对公
ALTER TABLE `tenant_bank_account`
    ADD COLUMN `account_type` tinyint(2) NOT NULL DEFAULT 1 COMMENT '账户类型（1-对公、2-对私）' AFTER `tenant_id`;
