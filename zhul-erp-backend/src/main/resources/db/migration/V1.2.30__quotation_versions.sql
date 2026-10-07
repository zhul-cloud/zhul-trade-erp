-- ===========================
-- 报价单多版本：已发送的报价单可出新版本（Rev.2、Rev.3…），编号不变
-- 型号行、费用行按版本存，is_current 标记当前版本（询盘状态、开 PI、成交计算只看当前版本）；
-- quotation 表头始终是当前版本，修改中的新版本表头存在 quotation_version
-- 见 openspec/changes/add-quotation-revisions
-- ===========================

CREATE TABLE IF NOT EXISTS `quotation_version` (
    `id`                bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`         int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `quotation_id`      bigint        NOT NULL DEFAULT 0  COMMENT '报价单ID，关联quotation.id',
    `version_no`        int(11)       NOT NULL DEFAULT 1  COMMENT '版本号（Rev.1 起，同一报价单内递增）',
    `status`            tinyint(2)    NOT NULL DEFAULT 1  COMMENT '版本状态（1-编辑中、2-已发送、3-已放弃）',
    `exchange_rate`     decimal(18,6) NOT NULL DEFAULT 1.000000 COMMENT '汇率快照（1外币=人民币）',
    `rate_time`         datetime      NULL                COMMENT '汇率快照对应的系统汇率更新时间',
    `incoterm`          varchar(16)   NOT NULL DEFAULT '' COMMENT '贸易术语',
    `incoterm_place`    varchar(64)   NOT NULL DEFAULT '' COMMENT '术语地点',
    `valid_until`       date          NULL                COMMENT '有效期至',
    `remark`            varchar(500)  NOT NULL DEFAULT '' COMMENT '备注（显示在报价单上）',
    `item_amount`       decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '小计（型号行小计之和，报价币种）',
    `fee_amount`        decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '费用合计（报价币种）',
    `total_amount`      decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '合计（报价币种）',
    `total_amount_cny`  decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '合计（本位币CNY）= 合计 × 汇率，HALF_UP 2位',
    `net_profit`        decimal(18,2) NULL                COMMENT '合计净利润（报价币种），不含费用',
    `net_profit_cny`    decimal(18,2) NULL                COMMENT '合计净利润（CNY）',
    `margin_rate`       decimal(5,2)  NULL                COMMENT '合计毛利率（%）',
    `sent_at`           datetime      NULL                COMMENT '该版本首次发送时间',
    `deleted_at`        datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`         varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`         varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_quotation_version` (`quotation_id`, `version_no`),
    KEY `idx_quotation_version_tenant` (`tenant_id`),
    KEY `idx_quotation_version_status` (`status`),
    KEY `idx_quotation_version_deleted` (`deleted_at`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='报价单版本（表头快照）';

ALTER TABLE `quotation`
    ADD COLUMN `current_version_no` int(11) NOT NULL DEFAULT 1 COMMENT '当前版本号（已发送报价单为当前有效版本，草稿为 1）' AFTER `copied_from_id`,
    ADD COLUMN `editing_version_no` int(11) NULL COMMENT '修改中的新版本号（已发送后点「修改」生成），没有时为空' AFTER `current_version_no`;

ALTER TABLE `quotation_item`
    ADD COLUMN `version_no` int(11)    NOT NULL DEFAULT 1 COMMENT '所属版本号' AFTER `quotation_id`,
    ADD COLUMN `is_current` tinyint(2) NOT NULL DEFAULT 1 COMMENT '是否当前版本（0-否、1-是）；询盘状态、开 PI、成交只看当前版本' AFTER `version_no`,
    ADD KEY `idx_quotation_item_current` (`quotation_id`, `is_current`);

ALTER TABLE `quotation_fee`
    ADD COLUMN `version_no` int(11)    NOT NULL DEFAULT 1 COMMENT '所属版本号' AFTER `quotation_id`,
    ADD COLUMN `is_current` tinyint(2) NOT NULL DEFAULT 1 COMMENT '是否当前版本（0-否、1-是）' AFTER `version_no`;

ALTER TABLE `quotation_send_log`
    ADD COLUMN `version_no` int(11) NOT NULL DEFAULT 1 COMMENT '发送的版本号' AFTER `quotation_id`;

-- 已发送过的报价单补 Rev.1 版本快照（草稿的表头就在 quotation 上，发送时再生成）
INSERT INTO `quotation_version` (`tenant_id`, `quotation_id`, `version_no`, `status`, `exchange_rate`, `rate_time`, `incoterm`,
                                 `incoterm_place`, `valid_until`, `remark`, `item_amount`, `fee_amount`, `total_amount`,
                                 `total_amount_cny`, `net_profit`, `net_profit_cny`, `margin_rate`, `sent_at`, `create_by`, `update_by`)
SELECT q.`tenant_id`, q.`id`, 1, 2, q.`exchange_rate`, q.`rate_time`, q.`incoterm`, q.`incoterm_place`, q.`valid_until`, q.`remark`,
       q.`item_amount`, q.`fee_amount`, q.`total_amount`, q.`total_amount_cny`, q.`net_profit`, q.`net_profit_cny`, q.`margin_rate`,
       q.`sent_at`, 'sys', 'sys'
FROM `quotation` q
WHERE q.`status` <> 1
  AND NOT EXISTS (SELECT 1 FROM `quotation_version` v WHERE v.`quotation_id` = q.`id` AND v.`version_no` = 1);
