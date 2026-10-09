-- ===========================
-- 到货与入库：供应商发货单、采购入库单、到货差异、暂存货、拍摄任务、业务附件（供应链第②期）
-- 见 openspec/changes/add-purchase-receiving
-- ===========================

-- ---------------------------------------------------------------- 业务附件
CREATE TABLE `biz_attachment` (
    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`    int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `owner_type`   varchar(16)  NOT NULL DEFAULT '' COMMENT '所属单据类型（SHIPMENT-供应商发货单、RECEIPT-采购入库单、SHOOT-拍摄任务）',
    `owner_id`     bigint       NOT NULL DEFAULT 0  COMMENT '所属单据ID；0 表示已上传、还没挂到单据上',
    `kind`         tinyint(2)   NOT NULL DEFAULT 1  COMMENT '类别（1-图片、2-视频）',
    `storage`      varchar(8)   NOT NULL DEFAULT 'LOCAL' COMMENT '存储方式（LOCAL-服务器私有目录、OSS-对象存储）',
    `file_key`     varchar(200) NOT NULL DEFAULT '' COMMENT '文件键',
    `url`          varchar(500) NOT NULL DEFAULT '' COMMENT '访问链接（对象存储时保存，本地为空）',
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
    KEY `idx_owner` (`owner_type`, `owner_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='业务附件（图片、视频）';

-- ---------------------------------------------------------------- 供应商发货单
CREATE TABLE `supplier_shipment` (
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `sd_no`       varchar(32)  NOT NULL DEFAULT '' COMMENT '发货单编号（如 SD20261010001）',
    `po_id`       bigint       NOT NULL DEFAULT 0  COMMENT '采购单ID，关联purchase_order.id',
    `source`      tinyint(2)   NOT NULL DEFAULT 1  COMMENT '来源（1-采购员登记、2-仓库补登）',
    `carrier`     varchar(32)  NOT NULL DEFAULT '' COMMENT '快递公司',
    `tracking_no` varchar(64)  NOT NULL DEFAULT '' COMMENT '快递单号',
    `ship_date`   date         NULL                COMMENT '发货日期',
    `status`      tinyint(2)   NOT NULL DEFAULT 1  COMMENT '状态（1-在途、2-已入库、3-已作废）',
    `void_reason` varchar(200) NOT NULL DEFAULT '' COMMENT '作废原因',
    `note`        varchar(300) NOT NULL DEFAULT '' COMMENT '备注',
    `deleted_at`  datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_sd_no` (`tenant_id`, `sd_no`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_po_id` (`po_id`),
    KEY `idx_status` (`status`),
    KEY `idx_tracking_no` (`tracking_no`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='供应商发货单';

CREATE TABLE `supplier_shipment_item` (
    `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`      int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `shipment_id`    bigint       NOT NULL DEFAULT 0  COMMENT '发货单ID，关联supplier_shipment.id',
    `po_item_id`     bigint       NOT NULL DEFAULT 0  COMMENT '采购单行ID，关联purchase_order_item.id',
    `requirement_id` bigint       NOT NULL DEFAULT 0  COMMENT '采购需求ID，关联purchase_requirement.id',
    `so_item_id`     bigint       NOT NULL DEFAULT 0  COMMENT '订单型号行ID，关联sales_order_item.id',
    `model`          varchar(128) NOT NULL DEFAULT '' COMMENT '型号',
    `brand`          varchar(64)  NOT NULL DEFAULT '' COMMENT '品牌',
    `category`       varchar(32)  NOT NULL DEFAULT '' COMMENT '品类',
    `quantity`       int(11)      NOT NULL DEFAULT 0  COMMENT '发货数量',
    `deleted_at`     datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`      varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`      varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_shipment_id` (`shipment_id`),
    KEY `idx_po_item_id` (`po_item_id`),
    KEY `idx_so_item_id` (`so_item_id`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='供应商发货单行';

-- ---------------------------------------------------------------- 采购入库单
CREATE TABLE `purchase_receipt` (
    `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`      int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `gr_no`          varchar(32)  NOT NULL DEFAULT '' COMMENT '入库单编号（如 GR20261011001）',
    `shipment_id`    bigint       NOT NULL DEFAULT 0  COMMENT '发货单ID，关联supplier_shipment.id',
    `po_id`          bigint       NOT NULL DEFAULT 0  COMMENT '采购单ID，关联purchase_order.id',
    `received_date`  date         NULL                COMMENT '收货日期',
    `received_by`    bigint       NOT NULL DEFAULT 0  COMMENT '收货人用户ID',
    `status`         tinyint(2)   NOT NULL DEFAULT 1  COMMENT '状态（1-有效、2-已冲销）',
    `reverse_reason` varchar(200) NOT NULL DEFAULT '' COMMENT '冲销原因',
    `note`           varchar(300) NOT NULL DEFAULT '' COMMENT '差异说明 / 备注',
    `deleted_at`     datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`      varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`      varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_gr_no` (`tenant_id`, `gr_no`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_shipment_id` (`shipment_id`),
    KEY `idx_po_id` (`po_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='采购入库单';

CREATE TABLE `purchase_receipt_item` (
    `id`               bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `receipt_id`       bigint       NOT NULL DEFAULT 0  COMMENT '入库单ID，关联purchase_receipt.id',
    `shipment_item_id` bigint       NOT NULL DEFAULT 0  COMMENT '发货单行ID，关联supplier_shipment_item.id',
    `po_item_id`       bigint       NOT NULL DEFAULT 0  COMMENT '采购单行ID，关联purchase_order_item.id',
    `requirement_id`   bigint       NOT NULL DEFAULT 0  COMMENT '采购需求ID，关联purchase_requirement.id',
    `so_item_id`       bigint       NOT NULL DEFAULT 0  COMMENT '订单型号行ID，关联sales_order_item.id',
    `model`            varchar(128) NOT NULL DEFAULT '' COMMENT '型号',
    `brand`            varchar(64)  NOT NULL DEFAULT '' COMMENT '品牌',
    `category`         varchar(32)  NOT NULL DEFAULT '' COMMENT '品类',
    `shipped_qty`      int(11)      NOT NULL DEFAULT 0  COMMENT '发货数量快照',
    `received_qty`     int(11)      NOT NULL DEFAULT 0  COMMENT '实收数量',
    `qualified_qty`    int(11)      NOT NULL DEFAULT 0  COMMENT '合格数量',
    `defective_qty`    int(11)      NOT NULL DEFAULT 0  COMMENT '不良数量',
    `note`             varchar(300) NOT NULL DEFAULT '' COMMENT '说明',
    `deleted_at`       datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`        varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`        varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_receipt_id` (`receipt_id`),
    KEY `idx_shipment_item_id` (`shipment_item_id`),
    KEY `idx_po_item_id` (`po_item_id`),
    KEY `idx_so_item_id` (`so_item_id`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='采购入库单行';

-- ---------------------------------------------------------------- 到货差异
CREATE TABLE `receiving_discrepancy` (
    `id`                 bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`          int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `receipt_id`         bigint        NOT NULL DEFAULT 0  COMMENT '入库单ID，关联purchase_receipt.id',
    `receipt_item_id`    bigint        NOT NULL DEFAULT 0  COMMENT '入库单行ID，关联purchase_receipt_item.id',
    `po_id`              bigint        NOT NULL DEFAULT 0  COMMENT '采购单ID，关联purchase_order.id',
    `po_item_id`         bigint        NOT NULL DEFAULT 0  COMMENT '采购单行ID，关联purchase_order_item.id',
    `purchaser_id`       bigint        NOT NULL DEFAULT 0  COMMENT '负责的采购员用户ID（采购单的采购员）',
    `model`              varchar(128)  NOT NULL DEFAULT '' COMMENT '型号',
    `brand`              varchar(64)   NOT NULL DEFAULT '' COMMENT '品牌',
    `category`           varchar(32)   NOT NULL DEFAULT '' COMMENT '品类',
    `type`               tinyint(2)    NOT NULL DEFAULT 1  COMMENT '差异类型（1-少发、2-不良、3-多发）',
    `quantity`           int(11)       NOT NULL DEFAULT 0  COMMENT '差异数量',
    `status`             tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（1-待处理、2-已处理）',
    `resolution`         tinyint(2)    NOT NULL DEFAULT 0  COMMENT '处理方式（0-未处理、1-等补发、2-不补了、3-退货换货、4-退货不补、5-折价接收、6-退回供应商、7-暂存）',
    `discount_amount`    decimal(18,2) NULL                COMMENT '折价金额（采购单币种），折价接收时填写',
    `return_carrier`     varchar(32)   NOT NULL DEFAULT '' COMMENT '退货快递公司',
    `return_tracking_no` varchar(64)   NOT NULL DEFAULT '' COMMENT '退货快递单号',
    `return_freight`     decimal(18,2) NULL                COMMENT '退货运费（CNY）',
    `free_of_charge`     tinyint(1)    NOT NULL DEFAULT 0  COMMENT '多发暂存时供应商是否白送（0-否、1-是）',
    `handled_by`         bigint        NULL                COMMENT '处理人用户ID',
    `handled_at`         datetime      NULL                COMMENT '处理时间',
    `note`               varchar(300)  NOT NULL DEFAULT '' COMMENT '处理说明',
    `deleted_at`         datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`          varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`          varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_receipt_id` (`receipt_id`),
    KEY `idx_receipt_item_id` (`receipt_item_id`),
    KEY `idx_po_id` (`po_id`),
    KEY `idx_po_item_id` (`po_item_id`),
    KEY `idx_purchaser_id` (`purchaser_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='到货差异';

-- ---------------------------------------------------------------- 暂存货
CREATE TABLE `stock_hold` (
    `id`             bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`      int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `discrepancy_id` bigint        NOT NULL DEFAULT 0  COMMENT '来源差异ID，关联receiving_discrepancy.id',
    `receipt_id`     bigint        NOT NULL DEFAULT 0  COMMENT '来源入库单ID，关联purchase_receipt.id',
    `po_id`          bigint        NOT NULL DEFAULT 0  COMMENT '来源采购单ID，关联purchase_order.id',
    `model`          varchar(128)  NOT NULL DEFAULT '' COMMENT '型号',
    `brand`          varchar(64)   NOT NULL DEFAULT '' COMMENT '品牌',
    `category`       varchar(32)   NOT NULL DEFAULT '' COMMENT '品类',
    `quantity`       int(11)       NOT NULL DEFAULT 0  COMMENT '数量',
    `cost_price`     decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '成本单价（CNY，不含税；供应商白送为 0）',
    `location_note`  varchar(100)  NOT NULL DEFAULT '' COMMENT '位置备注',
    `status`         tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（1-暂存中、2-已退回、3-已报废、4-已转样品）',
    `return_carrier`     varchar(32)   NOT NULL DEFAULT '' COMMENT '退回快递公司',
    `return_tracking_no` varchar(64)   NOT NULL DEFAULT '' COMMENT '退回快递单号',
    `return_freight`     decimal(18,2) NULL                COMMENT '退回运费（CNY）',
    `handled_by`     bigint        NULL                COMMENT '处置人用户ID',
    `handled_at`     datetime      NULL                COMMENT '处置时间',
    `handle_note`    varchar(300)  NOT NULL DEFAULT '' COMMENT '处置说明',
    `deleted_at`     datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`      varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`    datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`      varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_discrepancy_id` (`discrepancy_id`),
    KEY `idx_po_id` (`po_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='暂存货（多发且暂留的货）';

-- ---------------------------------------------------------------- 拍摄任务与素材
CREATE TABLE `shoot_task` (
    `id`                  bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`           int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `receipt_id`          bigint       NOT NULL DEFAULT 0  COMMENT '来源入库单ID，关联purchase_receipt.id',
    `receipt_item_id`     bigint       NOT NULL DEFAULT 0  COMMENT '来源入库单行ID，关联purchase_receipt_item.id',
    `so_id`               bigint       NOT NULL DEFAULT 0  COMMENT '来源销售订单ID，关联sales_order.id',
    `model`               varchar(128) NOT NULL DEFAULT '' COMMENT '型号',
    `brand`               varchar(64)  NOT NULL DEFAULT '' COMMENT '品牌',
    `category`            varchar(32)  NOT NULL DEFAULT '' COMMENT '品类',
    `asset_key`           varchar(200) NOT NULL DEFAULT '' COMMENT '素材键：品牌 + 型号（小写、去首尾空格）',
    `status`              tinyint(2)   NOT NULL DEFAULT 1  COMMENT '状态（1-待拍摄、2-已完成、3-已跳过）',
    `skip_reason`         varchar(200) NOT NULL DEFAULT '' COMMENT '跳过原因',
    `reused_from_task_id` bigint       NULL                COMMENT '复用了哪个任务的素材',
    `shooter_id`          bigint       NULL                COMMENT '拍摄人用户ID',
    `completed_at`        datetime     NULL                COMMENT '完成时间',
    `deleted_at`          datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`           varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`           varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_receipt_id` (`receipt_id`),
    KEY `idx_receipt_item_id` (`receipt_item_id`),
    KEY `idx_so_id` (`so_id`),
    KEY `idx_asset_key` (`asset_key`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='拍摄任务';

CREATE TABLE `media_asset` (
    `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `asset_key`     varchar(200) NOT NULL DEFAULT '' COMMENT '素材键：品牌 + 型号（小写、去首尾空格）',
    `brand`         varchar(64)  NOT NULL DEFAULT '' COMMENT '品牌',
    `model`         varchar(128) NOT NULL DEFAULT '' COMMENT '型号',
    `attachment_id` bigint       NOT NULL DEFAULT 0  COMMENT '附件ID，关联biz_attachment.id',
    `media_type`    tinyint(2)   NOT NULL DEFAULT 3  COMMENT '素材类型（1-拆箱视频、2-验货视频、3-实物图）',
    `task_id`       bigint       NOT NULL DEFAULT 0  COMMENT '拍摄任务ID，关联shoot_task.id',
    `deleted_at`    datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`     varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`     varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_asset_key` (`asset_key`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_attachment_id` (`attachment_id`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='型号素材（拍摄任务上传，归到品牌 + 型号名下）';

-- ---------------------------------------------------------------- 菜单与按钮
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
              SELECT 100089 AS id, 100082 AS pid, 'RS2100089' AS code, '供应商发货' AS name, 2 AS type, 7 AS sort, 'carryOut' AS li, '' AS lsi, '' AS di, '' AS dsi,
                     '/purchase/shipments' AS path, '' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
    UNION ALL SELECT 100090, 0,      'RS1100090', '仓库管理',   1, 4, 'inbox',     '', '', '', '/warehouse', '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 100091, 100090, 'RS2100091', '入库验收',   2, 1, 'import',    '', '', '', '/warehouse/receipts', '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 100092, 100090, 'RS2100092', '暂存货',     2, 2, 'database',  '', '', '', '/warehouse/holds', '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 100093, 100090, 'RS2100093', '拍摄任务',   2, 3, 'camera',    '', '', '', '/warehouse/shoots', '', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110191, 100089, 'RS3110191', '登记发货',     3, 1, '', '', '', '', '', 'purchase:shipment:create', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110192, 100089, 'RS3110192', '处理到货差异', 3, 2, '', '', '', '', '', 'purchase:discrepancy:handle', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110193, 100091, 'RS3110193', '验收入库',     3, 1, '', '', '', '', '', 'warehouse:receipt:create', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110194, 100092, 'RS3110194', '处置暂存货',   3, 1, '', '', '', '', '', 'warehouse:hold:handle', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110195, 100093, 'RS3110195', '拍摄',         3, 1, '', '', '', '', '', 'warehouse:shoot:edit', 1, '', 'sys', 'sys'
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.id);

-- 供应商排到供应商发货之后
UPDATE `resource` SET `sort` = 8, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100062;

UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(
                 JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(
        `menu_ids`, '$', 100089), '$', 100090), '$', 100091), '$', 100092), '$', 100093),
        '$', 110191), '$', 110192), '$', 110193), '$', 110194), '$', 110195)
WHERE `name` IN ('标准版', '旗舰版') AND NOT JSON_CONTAINS(`menu_ids`, '100089');

-- ---------------------------------------------------------------- 内置角色：仓库、拍摄（平台租户，所有租户共用）
INSERT INTO `role` (`tenant_id`, `code`, `name`, `permission_scope`, `status`, `is_built_in`, `remark`, `create_by`, `update_by`)
SELECT 0, v.code, v.name, 1, 1, 1, v.remark, 'sys', 'sys' FROM (
              SELECT 'ROLE_WH' AS code, '仓库' AS name, '入库验收、暂存货、拍摄任务；兼做拍摄的仓库人员用这个角色' AS remark
    UNION ALL SELECT 'ROLE_SHOOTER', '拍摄', '只有拍摄任务'
) v
WHERE NOT EXISTS (SELECT 1 FROM `role` r WHERE r.`code` = v.code AND r.`tenant_id` = 0);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT v.role_code, s.`id`, s.`code`
FROM (
              SELECT 'ROLE_WH' AS role_code, 100001 AS rid
    UNION ALL SELECT 'ROLE_WH', 100091 UNION ALL SELECT 'ROLE_WH', 100092 UNION ALL SELECT 'ROLE_WH', 100093
    UNION ALL SELECT 'ROLE_WH', 110193 UNION ALL SELECT 'ROLE_WH', 110194 UNION ALL SELECT 'ROLE_WH', 110195
    UNION ALL SELECT 'ROLE_SHOOTER', 100001 UNION ALL SELECT 'ROLE_SHOOTER', 100093 UNION ALL SELECT 'ROLE_SHOOTER', 110195
) v
JOIN `resource` s ON s.`id` = v.rid
WHERE NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = v.role_code AND y.`resource_id` = s.`id`);

-- 采购员（有「采购单」菜单的角色）：供应商发货、登记发货、处理到货差异
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, s.`id`, s.`code`
FROM `role_resource` x
JOIN `resource` s ON s.`id` IN (100089, 110191, 110192)
WHERE x.`resource_id` = 100088
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = s.`id`);

-- 内置租户管理员：全部
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` IN (100089, 100091, 100092, 100093, 110191, 110192, 110193, 110194, 110195)
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);
