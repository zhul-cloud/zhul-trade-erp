-- change finance-ap-fx-docs：应付单补备注（结汇单已有 remark varchar(500)）
ALTER TABLE `fin_payable`
    ADD COLUMN `remark` varchar(500) NOT NULL DEFAULT '' COMMENT '备注' AFTER `settled_by_migration`;
