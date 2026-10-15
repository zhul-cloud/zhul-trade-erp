-- 付款方式「私对私」（openspec private-account-payment）：法人等个人账户付给对方个人账户，记录付款个人账户
ALTER TABLE `fin_payment`
    MODIFY COLUMN `method` tinyint NOT NULL DEFAULT 1 COMMENT '付款方式（1-公司对公、2-员工垫付、3-私对私）；退款固定 1',
    ADD COLUMN `payer_account` varchar(100) NOT NULL DEFAULT '' COMMENT '私对私时的付款个人账户（如 法人 张三 · 招行尾号 1234）' AFTER `bank_account_id`;

ALTER TABLE `fin_reimbursement`
    ADD COLUMN `pay_method` tinyint(2) NOT NULL DEFAULT 1 COMMENT '付款方式（1-公司对公、3-私对私）' AFTER `status`,
    ADD COLUMN `pay_payer_account` varchar(100) NOT NULL DEFAULT '' COMMENT '私对私时的付款个人账户' AFTER `pay_account_id`;
