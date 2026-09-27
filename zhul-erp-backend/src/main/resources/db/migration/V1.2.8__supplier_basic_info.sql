-- V1.2.8：供应商基础信息扩展（编码、工商信息、地区地址、结算信息），
-- 见 openspec/changes/enrich-supplier-basic-info/。
ALTER TABLE `supplier`
    ADD COLUMN `supplier_code`        varchar(20)   NOT NULL DEFAULT '' COMMENT '供应商编码（字母数字，租户内未删除记录唯一；自动生成格式 SUP+5位补零主键）' AFTER `tenant_id`,
    ADD COLUMN `short_name`           varchar(50)   NOT NULL DEFAULT '' COMMENT '供应商简称' AFTER `name`,
    ADD COLUMN `supplier_type`        tinyint(2)    NOT NULL DEFAULT 0  COMMENT '供应商类型（0-未设置、1-生产商、2-经销商、3-服务商、4-代理商、5-其他）' AFTER `short_name`,
    ADD COLUMN `industry`             tinyint(2)    NOT NULL DEFAULT 0  COMMENT '所属行业（0-未设置、1-制造业、2-原材料、3-信息技术、4-物流运输、5-金融服务、6-其他）' AFTER `supplier_type`,
    ADD COLUMN `credit_code`          varchar(18)   NOT NULL DEFAULT '' COMMENT '统一社会信用代码（18位大写字母数字，租户内未删除记录唯一）' AFTER `industry`,
    ADD COLUMN `legal_representative` varchar(50)   NOT NULL DEFAULT '' COMMENT '法人代表' AFTER `credit_code`,
    ADD COLUMN `registered_capital`   DECIMAL(18,2) NULL                COMMENT '注册资本（单位：万元人民币，NULL表示未填写）' AFTER `legal_representative`,
    ADD COLUMN `established_date`     date          NULL                COMMENT '成立日期' AFTER `registered_capital`,
    ADD COLUMN `region`               varchar(100)  NOT NULL DEFAULT '' COMMENT '所在地区（省/市/区名称，用/分隔）' AFTER `contact_email`,
    ADD COLUMN `address`              varchar(200)  NOT NULL DEFAULT '' COMMENT '详细地址' AFTER `region`,
    ADD COLUMN `bank_name`            varchar(100)  NOT NULL DEFAULT '' COMMENT '开户银行（含支行）' AFTER `address`,
    ADD COLUMN `bank_account`         varchar(30)   NOT NULL DEFAULT '' COMMENT '银行账号（仅数字，展示时脱敏）' AFTER `bank_name`,
    ADD COLUMN `remark`               varchar(500)  NOT NULL DEFAULT '' COMMENT '备注' AFTER `main_brands`,
    ADD KEY `idx_supplier_code` (`supplier_code`),
    ADD KEY `idx_credit_code` (`credit_code`),
    ADD KEY `idx_supplier_type` (`supplier_type`);

-- 存量供应商（询盘内联创建的）补齐编码；LPAD 遇到超过 5 位的主键会截断，所以只对短主键补零
UPDATE `supplier`
SET `supplier_code` = CONCAT('SUP', IF(LENGTH(`id`) >= 5, `id`, LPAD(`id`, 5, '0')))
WHERE `supplier_code` = '';

-- 供应商导出按钮权限（pid=100062 供应商管理）
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`) VALUES
(110155, 100062, 'RS3110155', '导出供应商', 3, 5, '','','','', '', 'partner:supplier:export', 1,'','sys','sys');

UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 110155)
WHERE `name` IN ('标准版', '旗舰版');
