-- ===========================
-- 采购需求与采购单（供应链单据模型第①期）
-- 见 openspec/changes/add-purchase-order
-- ===========================

-- ---------------------------------------------------------------- 供应商默认付款条件
ALTER TABLE `supplier`
    ADD COLUMN `payment_terms` varchar(500) NOT NULL DEFAULT '' COMMENT '默认付款条件（JSON 数组：percent 比例、trigger 到期时点 1-下单后 2-发货前 3-入库后、days 入库后天数），空为未设置' AFTER `main_brands`;

-- ---------------------------------------------------------------- 采购需求
CREATE TABLE `purchase_requirement` (
    `id`                    bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`             int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `so_id`                 bigint        NOT NULL DEFAULT 0  COMMENT '来源销售订单ID，关联sales_order.id',
    `so_item_id`            bigint        NOT NULL DEFAULT 0  COMMENT '来源订单型号行ID，关联sales_order_item.id',
    `quotation_item_id`     bigint        NOT NULL DEFAULT 0  COMMENT '来源报价行ID，关联quotation_item.id；重新转订单时按它接回已下单数量，手动订单为 0',
    `model`                 varchar(128)  NOT NULL DEFAULT '' COMMENT '型号',
    `brand`                 varchar(64)   NOT NULL DEFAULT '' COMMENT '品牌',
    `quantity`              int(11)       NOT NULL DEFAULT 0  COMMENT '需求数量',
    `target_price`          decimal(18,2) NULL                COMMENT '目标价（CNY，不含税）：转订单时的采购成本价快照，为空表示没有目标价',
    `purchaser_id`          bigint        NULL                COMMENT '采购员用户ID，关联user_basic.id；为空表示未指定',
    `suggested_supplier_id` bigint        NULL                COMMENT '建议供应商ID，关联supplier.id',
    `suggested_channel`     tinyint(2)    NOT NULL DEFAULT 0  COMMENT '建议来源渠道（0-无、1-淘宝、2-1688、3-闲鱼、4-供应商、5-其他）',
    `suggested_shop_name`   varchar(100)  NOT NULL DEFAULT '' COMMENT '建议店铺名称（电商渠道且没有关联供应商时）',
    `status`                tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（1-有效、2-已关闭、3-订单已取消）',
    `deleted_at`            datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`             varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`             varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_so_id` (`so_id`),
    KEY `idx_so_item_id` (`so_item_id`),
    KEY `idx_quotation_item_id` (`quotation_item_id`),
    KEY `idx_purchaser_id` (`purchaser_id`),
    KEY `idx_suggested_supplier_id` (`suggested_supplier_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='采购需求';

-- ---------------------------------------------------------------- 采购单
CREATE TABLE `purchase_order` (
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `po_no`            varchar(32)   NULL                COMMENT '采购单编号（如 FWPO20261009001），确认下单时生成，草稿为空',
    `supplier_id`      bigint        NOT NULL DEFAULT 0  COMMENT '供应商ID，关联supplier.id',
    `purchaser_id`     bigint        NOT NULL DEFAULT 0  COMMENT '采购员用户ID，关联user_basic.id',
    `status`           tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（1-草稿、2-已下单、3-已取消）',
    `order_date`       date          NULL                COMMENT '下单日期，确认下单时填写',
    `ordered_at`       datetime      NULL                COMMENT '确认下单时间',
    `currency_code`    varchar(3)    NOT NULL DEFAULT 'CNY' COMMENT '币种',
    `exchange_rate`    decimal(18,6) NOT NULL DEFAULT 1.000000 COMMENT '汇率（1 外币 = X CNY）',
    `tax_included`     tinyint(1)    NOT NULL DEFAULT 0  COMMENT '是否含税（0-不含税、1-含税）',
    `tax_rate`         decimal(5,2)  NOT NULL DEFAULT 0.00 COMMENT '税率（%），不含税时为 0',
    `item_amount`      decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '货款（原币）',
    `fee_amount`       decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '其他费用（原币）',
    `total_amount`     decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '合计（原币）',
    `total_amount_cny` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '合计（CNY）',
    `target_amount`    decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '目标金额（CNY，有目标价各行的目标价 × 数量）',
    `bargain_amount`   decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '砍价金额合计（CNY），可为负',
    `payment_terms`    varchar(500)  NOT NULL DEFAULT '' COMMENT '付款条件（JSON 数组，结构同 supplier.payment_terms），空为未填',
    `contract_no`      varchar(64)   NOT NULL DEFAULT '' COMMENT '供应商合同编号',
    `contract_amount`  decimal(18,2) NULL                COMMENT '供应商合同金额（原币）',
    `cancel_reason`    varchar(200)  NOT NULL DEFAULT '' COMMENT '取消原因',
    `cancelled_by`     bigint        NULL                COMMENT '取消人用户ID',
    `cancelled_at`     datetime      NULL                COMMENT '取消时间',
    `deleted_at`       datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_po_no` (`tenant_id`, `po_no`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_supplier_id` (`supplier_id`),
    KEY `idx_purchaser_id` (`purchaser_id`),
    KEY `idx_status` (`status`),
    KEY `idx_order_date` (`order_date`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='采购单';

CREATE TABLE `purchase_order_item` (
    `id`             bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`      int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `po_id`          bigint        NOT NULL DEFAULT 0  COMMENT '采购单ID，关联purchase_order.id',
    `requirement_id` bigint        NOT NULL DEFAULT 0  COMMENT '采购需求ID，关联purchase_requirement.id',
    `so_id`          bigint        NOT NULL DEFAULT 0  COMMENT '来源销售订单ID，关联sales_order.id',
    `so_item_id`     bigint        NOT NULL DEFAULT 0  COMMENT '来源订单型号行ID，关联sales_order_item.id',
    `model`          varchar(128)  NOT NULL DEFAULT '' COMMENT '型号',
    `brand`          varchar(64)   NOT NULL DEFAULT '' COMMENT '品牌',
    `quantity`       int(11)       NOT NULL DEFAULT 0  COMMENT '采购数量',
    `unit_price`     decimal(18,2) NULL                COMMENT '单价（原币，含税与否按单头），草稿可为空',
    `net_price_cny`  decimal(18,2) NULL                COMMENT '不含税单价（CNY）= 单价 × 汇率 ÷ (1 + 税率)',
    `target_price`   decimal(18,2) NULL                COMMENT '目标价快照（CNY，不含税）',
    `amount`         decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '小计（原币）',
    `bargain_amount` decimal(18,2) NULL                COMMENT '砍价金额（CNY）=(目标价 − 不含税单价) × 数量，可为负；没有目标价或单价时为空',
    `sort_order`     int(11)       NOT NULL DEFAULT 0  COMMENT '排序',
    `deleted_at`     datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`      varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`      varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_po_id` (`po_id`),
    KEY `idx_requirement_id` (`requirement_id`),
    KEY `idx_so_id` (`so_id`),
    KEY `idx_so_item_id` (`so_item_id`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='采购单行';

CREATE TABLE `purchase_order_fee` (
    `id`          bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `po_id`       bigint        NOT NULL DEFAULT 0  COMMENT '采购单ID，关联purchase_order.id',
    `fee_name`    varchar(64)   NOT NULL DEFAULT '' COMMENT '费用名称（如运费、包装费）',
    `amount`      decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '金额（原币）',
    `sort_order`  int(11)       NOT NULL DEFAULT 0  COMMENT '排序',
    `deleted_at`  datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_po_id` (`po_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='采购单其他费用';

CREATE TABLE `purchase_order_attachment` (
    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`    int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `po_id`        bigint       NOT NULL DEFAULT 0  COMMENT '采购单ID，关联purchase_order.id',
    `file_key`     varchar(200) NOT NULL DEFAULT '' COMMENT '私有存储文件键',
    `file_name`    varchar(200) NOT NULL DEFAULT '' COMMENT '原文件名',
    `file_size`    bigint       NOT NULL DEFAULT 0  COMMENT '文件大小（字节）',
    `content_type` varchar(64)  NOT NULL DEFAULT '' COMMENT '文件类型',
    `uploaded_by`  bigint       NOT NULL DEFAULT 0  COMMENT '上传人用户ID',
    `deleted_at`   datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`    varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`    varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_po_id` (`po_id`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='采购单供应商合同附件';

CREATE TABLE `purchase_order_log` (
    `id`          bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `po_id`       bigint        NOT NULL DEFAULT 0  COMMENT '采购单ID，关联purchase_order.id',
    `action`      varchar(32)   NOT NULL DEFAULT '' COMMENT '操作（如 自动生成、确认下单、修改、取消）',
    `content`     varchar(1000) NOT NULL DEFAULT '' COMMENT '内容（修改前后的值）',
    `operator_id` bigint        NULL                COMMENT '操作人用户ID，系统自动生成时为空',
    `create_time` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_po_id` (`po_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='采购单操作日志';

-- ---------------------------------------------------------------- 存量回填
-- 有效订单中还在「待采购」的型号各生成一条需求；已推进到后面步骤的视为系统外已下单，不生成。
-- 存量需求不自动排入草稿，留在需求池由采购员生成。
INSERT INTO `purchase_requirement` (`tenant_id`, `so_id`, `so_item_id`, `quotation_item_id`, `model`, `brand`, `quantity`,
                                    `target_price`, `purchaser_id`, `suggested_supplier_id`, `suggested_channel`, `suggested_shop_name`,
                                    `status`, `create_by`, `update_by`)
SELECT i.`tenant_id`, i.`so_id`, i.`id`, i.`quotation_item_id`, i.`model`, i.`brand`, i.`quantity`,
       i.`cost_price`, i.`purchaser_id`,
       IF(s.`supplier_id` > 0, s.`supplier_id`, NULL),
       COALESCE(s.`channel`, 0),
       IF(s.`supplier_id` > 0 OR s.`shop_name` IS NULL, '', s.`shop_name`),
       1, 'sys', 'sys'
FROM `sales_order_item` i
JOIN `sales_order` o ON o.`id` = i.`so_id`
LEFT JOIN `quotation_item` q ON q.`id` = i.`quotation_item_id`
LEFT JOIN `sourcing_quote` s ON s.`id` = q.`cost_quote_id`
WHERE o.`status` = 1 AND o.`deleted_at` IS NULL AND i.`deleted_at` IS NULL
  AND i.`progress_code` = 'PENDING_PURCHASE'
  AND NOT EXISTS (SELECT 1 FROM `purchase_requirement` r WHERE r.`so_item_id` = i.`id`);

-- ---------------------------------------------------------------- 菜单与按钮
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
              SELECT 100087 AS id, 100082 AS pid, 'RS2100087' AS code, '采购需求' AS name, 2 AS type, 5 AS sort, 'unorderedList' AS li, '' AS lsi, '' AS di, '' AS dsi,
                     '/purchase/requirements' AS path, '' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
    UNION ALL SELECT 100088, 100082, 'RS2100088', '采购单',       2, 6, 'shopping', '', '', '', '/purchase/orders', '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110187, 100087, 'RS3110187', '拆分采购需求', 3, 1, '', '', '', '', '', 'purchase:requirement:split', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110188, 100087, 'RS3110188', '指派采购员',   3, 2, '', '', '', '', '', 'purchase:requirement:assign', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110189, 100088, 'RS3110189', '新建采购单',   3, 1, '', '', '', '', '', 'purchase:order:create', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110190, 100088, 'RS3110190', '取消采购单',   3, 2, '', '', '', '', '', 'purchase:order:cancel', 1, '', 'sys', 'sys'
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.id);

-- 供应商排到采购单之后
UPDATE `resource` SET `sort` = 7, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100062;

UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(
        `menu_ids`, '$', 100087), '$', 100088), '$', 110187), '$', 110188), '$', 110189), '$', 110190)
WHERE `name` IN ('标准版', '旗舰版') AND NOT JSON_CONTAINS(`menu_ids`, '100087');

-- 采购员（有「我的询价」的角色，兼职采购除外）与内置租户管理员：两个菜单、拆分、新建与取消采购单
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` IN (100087, 100088, 110187, 110189, 110190)
WHERE ((r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1)
       OR (r.`code` <> 'ROLE_PTBUYER' AND EXISTS (SELECT 1 FROM `role_resource` x WHERE x.`role_code` = r.`code` AND x.`resource_id` = 100055)))
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);

-- 指派采购员：采购负责人（有「询价分配」的角色）与内置租户管理员
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` IN (100087, 100088, 110188)
WHERE ((r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1)
       OR (r.`code` <> 'ROLE_PTBUYER' AND EXISTS (SELECT 1 FROM `role_resource` x WHERE x.`role_code` = r.`code` AND x.`resource_id` = 100054)))
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);
