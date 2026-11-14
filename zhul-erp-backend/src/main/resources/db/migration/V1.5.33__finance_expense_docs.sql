-- change finance-expense-docs：报销单单据化，备注可在详情页修改（附件沿用付款截图 REIMBURSE）
ALTER TABLE `fin_reimbursement`
    ADD COLUMN `remark` varchar(500) NOT NULL DEFAULT '' COMMENT '备注' AFTER `pay_remark`;
