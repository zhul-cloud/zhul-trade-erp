-- ===========================
-- 发货跟踪：采购单预计发货日期、发货单预计到货日期、快递时效
-- 见 openspec/changes/add-shipping-tracking
-- ===========================

ALTER TABLE `purchase_order`
    ADD COLUMN `expected_ship_date` date NULL COMMENT '预计发货日期（确认下单时必填，可改）' AFTER `ordered_at`,
    ADD KEY `idx_expected_ship_date` (`expected_ship_date`);

ALTER TABLE `supplier_shipment`
    ADD COLUMN `expected_arrival_date` date NULL COMMENT '预计到货日期（按快递时效估算，可改）' AFTER `ship_date`;

-- ---------------------------------------------------------------- 快递时效
CREATE TABLE `transit_time` (
    `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`       int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `carrier`         varchar(32)  NOT NULL DEFAULT '' COMMENT '快递公司',
    `origin_province` varchar(16)  NOT NULL DEFAULT '' COMMENT '发货省份（简称，如 上海、广东；空表示该快递公司的默认天数）',
    `days`            int(11)      NOT NULL DEFAULT 3  COMMENT '运输天数（到福州仓库）',
    `remark`          varchar(100) NOT NULL DEFAULT '' COMMENT '备注',
    `deleted_at`      datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`       varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`       varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_carrier` (`carrier`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='快递时效（发货省份到福州仓库的运输天数）';

-- 默认运输天数：平台模板，租户改了后生成租户自己的值
INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_type`, `is_builtin`, `is_encrypted`, `config_group`, `remark`, `create_by`, `update_by`)
SELECT 0, 'transit.default-days', '默认运输天数', '3', 'NUMBER', 1, 0, 'purchase', '快递公司没有时效规则、或发货单没填快递公司时按这个天数估算预计到货', 'sys', 'sys'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `tenant_id` = 0 AND `config_key` = 'transit.default-days' AND `deleted_at` IS NULL);

-- ---------------------------------------------------------------- 菜单与按钮
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
              SELECT 100094 AS id, 100084 AS pid, 'RS2100094' AS code, '快递时效' AS name, 2 AS type, 6 AS sort, 'clockCircle' AS li, '' AS lsi, '' AS di, '' AS dsi,
                     '/system/transit-times' AS path, '' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
    UNION ALL SELECT 110196, 100094, 'RS3110196', '编辑快递时效', 3, 1, '', '', '', '', '', 'system:transit-time:edit', 1, '', 'sys', 'sys'
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.id);

UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(`menu_ids`, '$', 100094), '$', 110196)
WHERE `name` IN ('标准版', '旗舰版') AND NOT JSON_CONTAINS(`menu_ids`, '100094');

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` IN (100094, 110196)
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);
