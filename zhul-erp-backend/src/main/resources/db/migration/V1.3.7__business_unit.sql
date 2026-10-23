-- 事业部核算（openspec business-unit-accounting）：
-- 1. 部门可以标记为「事业部」（独立核算的合伙人团队），记录开始核算月份；
-- 2. 源头单据记录所属事业部（department.id，NULL-公共或公司没有设置事业部）：客户、商机、询盘、报价、PI、销售订单、
--    收款、收款账户、结汇单、费用单、其他收入；采购、应付、付款、退款、出运通过销售订单取，不重复存；
-- 3. 收款记录「账户与订单事业部不一致」标记；
-- 4. 公共费用最低分摊比例（平台模板默认 20%）。
-- 已有数据不在这里回填：公司在部门管理标记第一个事业部时，已有单据全部归入它。

ALTER TABLE `department`
    ADD COLUMN `is_business_unit` tinyint(2) NOT NULL DEFAULT 0 COMMENT '是否事业部（0-否、1-是）' AFTER `status`,
    ADD COLUMN `bu_start_month` date NULL COMMENT '事业部开始核算月份（当月 1 日；非事业部为 NULL）' AFTER `is_business_unit`;

ALTER TABLE `customer`
    ADD COLUMN `business_unit_id` int(11) NULL COMMENT '所属事业部（department.id；NULL-公司没有设置事业部）',
    ADD INDEX `idx_business_unit_id` (`business_unit_id`);
ALTER TABLE `opportunity`
    ADD COLUMN `business_unit_id` int(11) NULL COMMENT '所属事业部（department.id，同客户；NULL-公司没有设置事业部）',
    ADD INDEX `idx_business_unit_id` (`business_unit_id`);
ALTER TABLE `customer_inquiry`
    ADD COLUMN `business_unit_id` int(11) NULL COMMENT '所属事业部（department.id，创建时取客户的；NULL-公司没有设置事业部）',
    ADD INDEX `idx_business_unit_id` (`business_unit_id`);
ALTER TABLE `quotation`
    ADD COLUMN `business_unit_id` int(11) NULL COMMENT '所属事业部（department.id，创建时取客户的；NULL-公司没有设置事业部）',
    ADD INDEX `idx_business_unit_id` (`business_unit_id`);
ALTER TABLE `proforma_invoice`
    ADD COLUMN `business_unit_id` int(11) NULL COMMENT '所属事业部（department.id，创建时取客户的；NULL-公司没有设置事业部）',
    ADD INDEX `idx_business_unit_id` (`business_unit_id`);
ALTER TABLE `sales_order`
    ADD COLUMN `business_unit_id` int(11) NULL COMMENT '所属事业部（department.id，创建时取 PI 或客户的；NULL-公司没有设置事业部）',
    ADD INDEX `idx_business_unit_id` (`business_unit_id`);
ALTER TABLE `payment_receipt`
    ADD COLUMN `business_unit_id` int(11) NULL COMMENT '所属事业部（department.id：已关联 PI 或订单的取订单，未认领的取收款账户；NULL-公司没有设置事业部）',
    ADD COLUMN `bu_mismatch` tinyint(2) NOT NULL DEFAULT 0 COMMENT '收款账户与订单的事业部不一致（0-否、1-是）',
    ADD INDEX `idx_business_unit_id` (`business_unit_id`);
ALTER TABLE `tenant_bank_account`
    ADD COLUMN `business_unit_id` int(11) NULL COMMENT '所属事业部（department.id；NULL-公司没有设置事业部）',
    ADD INDEX `idx_business_unit_id` (`business_unit_id`);
ALTER TABLE `fin_fx_settlement`
    ADD COLUMN `business_unit_id` int(11) NULL COMMENT '所属事业部（department.id，只分摊本事业部的到账；NULL-公司没有设置事业部）',
    ADD INDEX `idx_business_unit_id` (`business_unit_id`);
ALTER TABLE `fin_expense`
    ADD COLUMN `business_unit_id` int(11) NULL COMMENT '所属事业部（department.id；NULL-公共，按销售额比例分摊，或公司没有设置事业部）',
    ADD INDEX `idx_business_unit_id` (`business_unit_id`);
ALTER TABLE `fin_other_income`
    ADD COLUMN `business_unit_id` int(11) NULL COMMENT '所属事业部（department.id；NULL-公共，按销售额比例分摊，或公司没有设置事业部）',
    ADD INDEX `idx_business_unit_id` (`business_unit_id`);

INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_type`, `is_builtin`, `is_encrypted`, `config_group`, `remark`)
VALUES (0, 'finance.bu.min-share-percent', '事业部最低分摊比例（%）', '20', 'NUMBER', 1, 0, 'finance',
        '公共费用按销售额比例分给各事业部时，每个事业部至少承担的比例');
