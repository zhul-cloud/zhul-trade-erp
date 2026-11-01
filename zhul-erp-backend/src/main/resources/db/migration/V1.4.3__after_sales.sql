-- 售后管理（OpenSpec 变更 after-sales）
-- 售后单与跟进记录、退货单（退货入库、去向）、退款申请；退款单、费用单关联售后单；闲置库存来源「售后退货」；
-- 字典、菜单「销售管理 → 售后管理」「仓库管理 → 退货入库」、按钮与授权、退款申请审批开关。
-- 回退：见 openspec/changes/after-sales/design.md 的 Migration Plan，回退 SQL 在文件末尾的注释中。

-- ---------------------------------------------------------------- 售后单

CREATE TABLE `after_sales` (
    `id`              bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`       int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `as_no`           varchar(32)   NOT NULL DEFAULT '' COMMENT '售后单编号（如 AS20261102001）',
    `customer_id`     bigint        NOT NULL            COMMENT '客户ID，关联customer.id',
    `so_id`           bigint        NULL                COMMENT '销售订单ID，关联sales_order.id；没有订单时为空',
    `level_code`      varchar(32)   NOT NULL DEFAULT 'PENDING' COMMENT '问题等级（字典 after_sales_level 编码）',
    `symptom`         varchar(1000) NOT NULL DEFAULT '' COMMENT '问题现象',
    `customer_request` varchar(500) NOT NULL DEFAULT '' COMMENT '客户诉求',
    `received_at`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收到客户反馈的时间',
    `reason_code`     varchar(32)   NOT NULL DEFAULT 'INVESTIGATING' COMMENT '原因与责任（字典 after_sales_reason 编码）',
    `solutions`       varchar(200)  NOT NULL DEFAULT '' COMMENT '处理方案（字典 after_sales_solution 编码，逗号分隔）',
    `owner_id`        bigint        NOT NULL DEFAULT 0  COMMENT '负责人用户ID',
    `next_action`     varchar(300)  NOT NULL DEFAULT '' COMMENT '下一步行动',
    `next_update_at`  datetime      NULL                COMMENT '下次更新客户时间',
    `status`          tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（1-处理中、2-待客户确认、3-已结案、4-已撤销）',
    `result`          varchar(1000) NOT NULL DEFAULT '' COMMENT '结案处理结果',
    `closed_at`       datetime      NULL                COMMENT '结案时间',
    `cancel_reason`   varchar(200)  NOT NULL DEFAULT '' COMMENT '撤销原因',
    `deleted_at`      datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`       varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`       varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_as_no` (`tenant_id`, `as_no`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_so_id` (`so_id`),
    KEY `idx_owner_id` (`owner_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后单';

CREATE TABLE `after_sales_item` (
    `id`              bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`       int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `after_sales_id`  bigint        NOT NULL            COMMENT '售后单ID，关联after_sales.id',
    `so_item_id`      bigint        NULL                COMMENT '订单型号行ID，关联sales_order_item.id；手填型号时为空',
    `model`           varchar(128)  NOT NULL DEFAULT '' COMMENT '型号',
    `brand`           varchar(64)   NOT NULL DEFAULT '' COMMENT '品牌',
    `quantity`        int(11)       NOT NULL DEFAULT 1  COMMENT '数量',
    `deleted_at`      datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`       varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`       varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_after_sales_id` (`after_sales_id`),
    KEY `idx_so_item_id` (`so_item_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后单型号行';

CREATE TABLE `after_sales_log` (
    `id`              bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`       int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `after_sales_id`  bigint        NOT NULL            COMMENT '售后单ID，关联after_sales.id',
    `log_type`        varchar(16)   NOT NULL DEFAULT 'NOTE' COMMENT '类型（NOTE-跟进、LEVEL-等级、STATUS-状态、PLAN-处理、RETURN-退货、REFUND-退款、EXPENSE-费用、CREATE-创建）',
    `content`         varchar(1000) NOT NULL DEFAULT '' COMMENT '内容',
    `operator_id`     bigint        NOT NULL DEFAULT 0  COMMENT '操作人用户ID',
    `deleted_at`      datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`       varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`       varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_after_sales_id` (`after_sales_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后单跟进记录';

-- ---------------------------------------------------------------- 退货单

CREATE TABLE `sales_return` (
    `id`              bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`       int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `return_no`       varchar(32)   NOT NULL DEFAULT '' COMMENT '退货单编号（如 TH20261102001）',
    `after_sales_id`  bigint        NOT NULL            COMMENT '售后单ID，关联after_sales.id',
    `so_id`           bigint        NOT NULL            COMMENT '销售订单ID，关联sales_order.id',
    `carrier`         varchar(32)   NOT NULL DEFAULT '' COMMENT '客户寄回的快递公司',
    `tracking_no`     varchar(64)   NOT NULL DEFAULT '' COMMENT '客户寄回的快递单号',
    `remark`          varchar(300)  NOT NULL DEFAULT '' COMMENT '说明',
    `status`          tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（1-待入库、2-待处理、3-已处理、4-已作废）',
    `received_date`   date          NULL                COMMENT '收货日期',
    `received_by`     bigint        NULL                COMMENT '验收人用户ID',
    `receive_note`    varchar(300)  NOT NULL DEFAULT '' COMMENT '验收说明',
    `void_reason`     varchar(200)  NOT NULL DEFAULT '' COMMENT '作废原因',
    `deleted_at`      datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`       varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`       varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_return_no` (`tenant_id`, `return_no`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_after_sales_id` (`after_sales_id`),
    KEY `idx_so_id` (`so_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退货单';

CREATE TABLE `sales_return_item` (
    `id`              bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`       int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `return_id`       bigint        NOT NULL            COMMENT '退货单ID，关联sales_return.id',
    `so_item_id`      bigint        NOT NULL            COMMENT '订单型号行ID，关联sales_order_item.id',
    `model`           varchar(128)  NOT NULL DEFAULT '' COMMENT '型号',
    `brand`           varchar(64)   NOT NULL DEFAULT '' COMMENT '品牌',
    `quantity`        int(11)       NOT NULL            COMMENT '退货数量',
    `received_qty`    int(11)       NULL                COMMENT '实收数量（验收后）',
    `qualified_qty`   int(11)       NULL                COMMENT '合格数量（验收后）',
    `deleted_at`      datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`       varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`       varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_return_id` (`return_id`),
    KEY `idx_so_item_id` (`so_item_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退货单型号行';

CREATE TABLE `sales_return_disposition` (
    `id`              bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`       int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `return_id`       bigint        NOT NULL            COMMENT '退货单ID，关联sales_return.id',
    `return_item_id`  bigint        NOT NULL            COMMENT '退货行ID，关联sales_return_item.id',
    `so_id`           bigint        NOT NULL            COMMENT '销售订单ID，关联sales_order.id',
    `so_item_id`      bigint        NOT NULL            COMMENT '订单型号行ID，关联sales_order_item.id',
    `disposition`     tinyint(2)    NOT NULL            COMMENT '去向（1-退回供应商、2-转闲置库存、3-报废）',
    `quantity`        int(11)       NOT NULL            COMMENT '数量',
    `supplier_id`     bigint        NULL                COMMENT '退回的供应商ID，关联supplier.id',
    `tracking_no`     varchar(64)   NOT NULL DEFAULT '' COMMENT '退回快递单号',
    `freight_cny`     decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '退回运费（CNY，计入原订单成本）',
    `supplier_refund_cny` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '供应商退款或抵扣（CNY）',
    `cost_cny`        decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '冲减原订单成本（CNY）：退回供应商 = 供应商退款，转闲置 = 数量 × 原采购成本（HALF_UP 两位），报废为 0',
    `hold_id`         bigint        NULL                COMMENT '转闲置库存生成的闲置库存ID，关联stock_hold.id',
    `remark`          varchar(300)  NOT NULL DEFAULT '' COMMENT '说明（报废原因等）',
    `operator_id`     bigint        NOT NULL DEFAULT 0  COMMENT '操作人用户ID',
    `deleted_at`      datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`       varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`       varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_return_id` (`return_id`),
    KEY `idx_so_id` (`so_id`),
    KEY `idx_so_item_id` (`so_item_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退货去向';

-- ---------------------------------------------------------------- 退款申请

CREATE TABLE `fin_refund_request` (
    `id`              bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`       int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `request_no`      varchar(32)   NOT NULL DEFAULT '' COMMENT '退款申请编号（如 RA20261102001）',
    `after_sales_id`  bigint        NULL                COMMENT '售后单ID，关联after_sales.id；从订单详情提出时可为空',
    `so_id`           bigint        NOT NULL            COMMENT '销售订单ID，关联sales_order.id',
    `currency_code`   varchar(8)    NOT NULL            COMMENT '币种（订单币种）',
    `amount`          decimal(18,2) NOT NULL            COMMENT '申请金额（原币）',
    `reason`          varchar(32)   NOT NULL DEFAULT '' COMMENT '原因（字典 refund_reason 编码）',
    `method`          tinyint(2)    NOT NULL            COMMENT '建议去向（1-原路退回平台、2-银行汇款、3-转客户余额）',
    `payee_info`      varchar(300)  NOT NULL DEFAULT '' COMMENT '客户收款信息',
    `remark`          varchar(500)  NOT NULL DEFAULT '' COMMENT '说明',
    `status`          tinyint(2)    NOT NULL DEFAULT 2  COMMENT '状态（1-待审批、2-待财务处理、3-已执行、4-已驳回、5-已撤回）',
    `applicant_id`    bigint        NOT NULL DEFAULT 0  COMMENT '申请人用户ID',
    `approver_id`     bigint        NULL                COMMENT '审批人用户ID',
    `approved_at`     datetime      NULL                COMMENT '审批时间',
    `handler_id`      bigint        NULL                COMMENT '财务处理人用户ID',
    `handled_at`      datetime      NULL                COMMENT '处理时间',
    `reject_reason`   varchar(200)  NOT NULL DEFAULT '' COMMENT '驳回原因',
    `refund_id`       bigint        NULL                COMMENT '执行生成的退款单ID，关联fin_customer_refund.id',
    `deleted_at`      datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`       varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`       varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_request_no` (`tenant_id`, `request_no`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_after_sales_id` (`after_sales_id`),
    KEY `idx_so_id` (`so_id`),
    KEY `idx_refund_id` (`refund_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款申请';

-- ---------------------------------------------------------------- 关联字段

ALTER TABLE `fin_customer_refund`
    ADD COLUMN `after_sales_id`    bigint NULL COMMENT '关联售后单ID，关联after_sales.id' AFTER `so_id`,
    ADD COLUMN `refund_request_id` bigint NULL COMMENT '来源退款申请ID，关联fin_refund_request.id' AFTER `after_sales_id`,
    ADD INDEX `idx_refund_after_sales` (`after_sales_id`);

ALTER TABLE `fin_expense`
    ADD COLUMN `after_sales_id` bigint NULL COMMENT '关联售后单ID，关联after_sales.id（订单费用的补寄运费、维修费等）' AFTER `category`,
    ADD INDEX `idx_expense_after_sales` (`after_sales_id`);

ALTER TABLE `stock_hold`
    MODIFY COLUMN `source_type` tinyint(2) NOT NULL DEFAULT 1 COMMENT '来源（1-到货差异、2-订单部分取消、3-售后退货）',
    ADD COLUMN `return_id` bigint NOT NULL DEFAULT 0 COMMENT '来源退货单ID（售后退货），关联sales_return.id' AFTER `so_item_id`;

-- ---------------------------------------------------------------- 字典

INSERT INTO `dict_type` (`tenant_id`, `dict_type`, `dict_name`, `is_builtin`, `status`, `remark`, `create_by`, `update_by`)
SELECT 0, v.t, v.n, 1, 1, v.r, 'sys', 'sys'
FROM (
              SELECT 'after_sales_level' AS t, '售后问题等级' AS n, '按安全风险与设备影响定优先级，不代表责任' AS r
    UNION ALL SELECT 'after_sales_reason', '售后原因', '售后单的原因与责任'
    UNION ALL SELECT 'after_sales_solution', '售后方案', '售后单的处理方案，可多选'
) v
WHERE NOT EXISTS (SELECT 1 FROM `dict_type` d WHERE d.`dict_type` = v.t AND d.`tenant_id` = 0);

INSERT INTO `dict_item` (`tenant_id`, `dict_type_id`, `dict_type`, `item_code`, `item_name`, `item_name_en`, `item_value`, `sort_order`, `is_default`, `status`, `remark`, `create_by`, `update_by`)
SELECT 0, t.`id`, t.`dict_type`, v.code, v.name, '', v.code, v.s, v.d, 1, v.r, 'sys', 'sys'
FROM `dict_type` t
JOIN (
              SELECT 'after_sales_level' AS dt, 'PENDING' AS code, '待确认' AS name, 0 AS s, 1 AS d, '还没判定等级' AS r
    UNION ALL SELECT 'after_sales_level', 'S1', 'S1 安全或重大风险', 1, 0, '可能造成人身伤害、火灾或设备进一步损坏；先停机断电隔离'
    UNION ALL SELECT 'after_sales_level', 'S2', 'S2 设备停机', 2, 0, '设备或产线无法完成主要功能'
    UNION ALL SELECT 'after_sales_level', 'S3', 'S3 功能受限', 3, 0, '部分功能受影响或间歇异常'
    UNION ALL SELECT 'after_sales_level', 'S4', 'S4 外观附件', 4, 0, '不影响安全与核心功能'
    UNION ALL SELECT 'after_sales_level', 'S5', 'S5 使用咨询', 5, 0, '操作、配置、选型或兼容性咨询'
    UNION ALL SELECT 'after_sales_reason', 'INVESTIGATING', '调查中', 0, 1, '还没确认原因与责任'
    UNION ALL SELECT 'after_sales_reason', 'QUALITY', '产品质量', 1, 0, ''
    UNION ALL SELECT 'after_sales_reason', 'TRANSPORT', '运输包装', 2, 0, ''
    UNION ALL SELECT 'after_sales_reason', 'SELECTION', '选型兼容', 3, 0, ''
    UNION ALL SELECT 'after_sales_reason', 'INSTALL', '安装使用', 4, 0, '安装、接线、参数或使用环境'
    UNION ALL SELECT 'after_sales_reason', 'WARRANTY', '超保或正常损耗', 5, 0, ''
    UNION ALL SELECT 'after_sales_solution', 'REMOTE', '远程指导', 1, 0, ''
    UNION ALL SELECT 'after_sales_solution', 'PARTS', '补寄配件', 2, 0, ''
    UNION ALL SELECT 'after_sales_solution', 'LOCAL_REPAIR', '当地维修', 3, 0, ''
    UNION ALL SELECT 'after_sales_solution', 'RETURN_REPAIR', '退回检测或维修', 4, 0, ''
    UNION ALL SELECT 'after_sales_solution', 'EXCHANGE', '换货', 5, 0, ''
    UNION ALL SELECT 'after_sales_solution', 'REFUND', '退款', 6, 0, ''
    UNION ALL SELECT 'after_sales_solution', 'OTHER', '其他', 7, 0, ''
) v ON v.dt = t.`dict_type`
WHERE t.`tenant_id` = 0
  AND NOT EXISTS (SELECT 1 FROM `dict_item` i WHERE i.`dict_type` = v.dt AND i.`item_code` = v.code AND i.`tenant_id` = 0);

-- ---------------------------------------------------------------- 系统设置：退款申请审批开关

INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_type`, `is_builtin`, `is_encrypted`, `config_group`, `remark`, `create_by`, `update_by`)
SELECT 0, 'finance.refund-request.approval', '退款申请需要审批', 'false', 'BOOLEAN', 1, 0, 'finance',
       '打开后业务员提交的退款申请先由有「审批退款申请」权限的人通过，再交财务处理', 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` c WHERE c.`tenant_id` = 0 AND c.`config_key` = 'finance.refund-request.approval');

-- ---------------------------------------------------------------- 菜单与按钮

INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
              SELECT 100120 AS id, 100081 AS pid, 'RS2100120' AS code, '售后管理' AS name, 2 AS type, 7 AS sort, 'life-buoy' AS li, '' AS lsi, '' AS di, '' AS dsi,
                     '/sales/after-sales' AS path, '' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
    UNION ALL SELECT 100121, 100090, 'RS2100121', '退货入库', 2, 3, 'package-open', '', '', '', '/warehouse/returns', '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110221, 100121, 'RS3110221', '退货验收', 3, 1, '', '', '', '', '', 'warehouse:return:receive', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110222, 100121, 'RS3110222', '退货去向处理', 3, 2, '', '', '', '', '', 'purchase:return:dispose', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110223, 100110, 'RS3110223', '审批退款申请', 3, 2, '', '', '', '', '', 'finance:refund-request:approve', 1, '', 'sys', 'sys'
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.id);

-- 仓库管理排序：采购入库、销售出库、退货入库、闲置库存、实拍任务（system/navigation）
UPDATE `resource` SET `sort` = CASE `id` WHEN 100091 THEN 1 WHEN 100095 THEN 2 WHEN 100121 THEN 3 WHEN 100092 THEN 4 WHEN 100093 THEN 5 END,
    `update_time` = NOW()
WHERE `id` IN (100091, 100095, 100121, 100092, 100093);

-- 套餐：含销售订单的补售后管理；含采购入库或采购订单的补退货入库与两个按钮；含退款管理的补审批按钮
UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 100120)
WHERE JSON_CONTAINS(`menu_ids`, '100079') AND NOT JSON_CONTAINS(`menu_ids`, '100120');
UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(`menu_ids`, '$', 100121), '$', 110221), '$', 110222)
WHERE (JSON_CONTAINS(`menu_ids`, '100091') OR JSON_CONTAINS(`menu_ids`, '100088')) AND NOT JSON_CONTAINS(`menu_ids`, '100121');
UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 110223)
WHERE JSON_CONTAINS(`menu_ids`, '100110') AND NOT JSON_CONTAINS(`menu_ids`, '110223');

-- 角色：有销售订单菜单的得到售后管理；有采购入库菜单的得到退货入库与「退货验收」；有采购订单菜单的得到退货入库与「退货去向处理」；
-- 「审批退款申请」只授予租户管理员
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, 100120, 'RS2100120' FROM `role_resource` x
WHERE x.`resource_id` = 100079
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = 100120);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, 100121, 'RS2100121' FROM `role_resource` x
WHERE x.`resource_id` IN (100091, 100088)
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = 100121);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, 110221, 'RS3110221' FROM `role_resource` x
WHERE x.`resource_id` = 100091
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = 110221);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, 110222, 'RS3110222' FROM `role_resource` x
WHERE x.`resource_id` = 100088
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = 110222);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` IN (100120, 100121, 110221, 110222, 110223)
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);

-- ---------------------------------------------------------------- 回退（手动执行；先确认没有售后单、退货单与退款申请，或已导出留档）
-- DELETE FROM `role_resource` WHERE `resource_id` IN (100120, 100121, 110221, 110222, 110223);
-- DELETE FROM `resource` WHERE `id` IN (100120, 100121, 110221, 110222, 110223);
-- （tenant_package.menu_ids 中多出的 ID 不影响旧版本，可保留）
-- DELETE FROM `sys_config` WHERE `tenant_id` = 0 AND `config_key` = 'finance.refund-request.approval';
-- DELETE FROM `dict_item` WHERE `tenant_id` = 0 AND `dict_type` IN ('after_sales_level', 'after_sales_reason', 'after_sales_solution');
-- DELETE FROM `dict_type` WHERE `tenant_id` = 0 AND `dict_type` IN ('after_sales_level', 'after_sales_reason', 'after_sales_solution');
-- ALTER TABLE `stock_hold` DROP COLUMN `return_id`;
-- ALTER TABLE `fin_expense` DROP INDEX `idx_expense_after_sales`, DROP COLUMN `after_sales_id`;
-- ALTER TABLE `fin_customer_refund` DROP INDEX `idx_refund_after_sales`, DROP COLUMN `after_sales_id`, DROP COLUMN `refund_request_id`;
-- DROP TABLE `fin_refund_request`;
-- DROP TABLE `sales_return_disposition`;
-- DROP TABLE `sales_return_item`;
-- DROP TABLE `sales_return`;
-- DROP TABLE `after_sales_log`;
-- DROP TABLE `after_sales_item`;
-- DROP TABLE `after_sales`;
