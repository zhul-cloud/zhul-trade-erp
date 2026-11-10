-- V1.5.22：系统通知（openspec system-notification）。
-- 新增站内通知、通知接收人两张表；「业务设置」下新增「通知接收人」菜单与编辑按钮；采购需求超时小时数配置。
-- 岗位默认接收人不在迁移里写，运行时按权限算（某岗位从没保存过时取默认值）。
-- 回退：代码回退后两张表不再被读取；需要删除时
--   DROP TABLE `notification`; DROP TABLE `notification_receiver`;
--   DELETE FROM `role_resource` WHERE `resource_id` IN (100123, 110230);
--   DELETE FROM `resource` WHERE `id` IN (100123, 110230);
--   DELETE FROM `sys_config` WHERE `config_key` = 'purchase.requirement.overdue-hours';

CREATE TABLE `notification` (
    `id`           bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`    int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `recipient_id` bigint        NOT NULL DEFAULT 0  COMMENT '收件人（user_basic.id）',
    `node_code`    varchar(32)   NOT NULL DEFAULT '' COMMENT '通知节点编码（见 NotificationNode）',
    `kind`         tinyint(2)    NOT NULL DEFAULT 1  COMMENT '类型（1-待办、2-知会）',
    `merge_key`    varchar(64)   NOT NULL DEFAULT '' COMMENT '合并键：同一收件人、节点、合并键的待处理通知只有一条',
    `biz_no`       varchar(64)   NOT NULL DEFAULT '' COMMENT '相关单据编号',
    `link`         varchar(255)  NOT NULL DEFAULT '' COMMENT '点击后打开的页面路径',
    `biz_ids`      varchar(2000) NOT NULL DEFAULT '[]' COMMENT '待办涉及的记录ID（JSON 数组），判断是否已处理用',
    `item_count`   int(11)       NOT NULL DEFAULT 0  COMMENT '涉及的数量（任务数、型号数等）',
    `content`      varchar(500)  NOT NULL DEFAULT '' COMMENT '补充说明',
    `urgent`       tinyint(2)    NOT NULL DEFAULT 0  COMMENT '是否紧急（0-否、1-是）',
    `read_at`      datetime      NULL                COMMENT '已读时间，NULL表示未读',
    `done_at`      datetime      NULL                COMMENT '待办已处理时间，NULL表示未处理；知会型始终为NULL',
    `deleted_at`   datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`  datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`    varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`  datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`    varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_recipient` (`tenant_id`, `recipient_id`, `done_at`, `read_at`),
    KEY `idx_node_merge` (`tenant_id`, `node_code`, `merge_key`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='站内通知';

CREATE TABLE `notification_receiver` (
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)     NOT NULL DEFAULT 0  COMMENT '租户ID',
    `post_code`   varchar(32) NOT NULL DEFAULT '' COMMENT '岗位（PURCHASE_LEAD-采购负责人、RECEIVER-收货人、FINANCE-财务）',
    `user_id`     bigint      NOT NULL DEFAULT 0  COMMENT '接收人（user_basic.id）；收货人岗位为 0 表示「采购订单的采购员」',
    `status`      tinyint(2)  NOT NULL DEFAULT 1  COMMENT '状态（0-已移除、1-有效）',
    `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_post` (`tenant_id`, `post_code`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='通知接收人（按岗位）';

-- 采购需求超时时限：平台模板，租户改了后生成租户自己的值
INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_type`, `is_builtin`, `is_encrypted`, `config_group`, `remark`, `create_by`, `update_by`)
SELECT 0, 'purchase.requirement.overdue-hours', '采购需求超时小时数', '24', 'INTEGER', 1, 0, 'purchase', '订单生成后超过这么多小时还没下单的采购需求在列表中标红', 'sys', 'sys'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` WHERE `tenant_id` = 0 AND `config_key` = 'purchase.requirement.overdue-hours' AND `deleted_at` IS NULL);

-- ---------------------------------------------------------------- 菜单与按钮：业务设置 → 通知接收人
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
              SELECT 100123 AS id, 100084 AS pid, 'RS2100123' AS code, '通知接收人' AS name, 2 AS type, 9 AS sort, 'bell' AS li, '' AS lsi, '' AS di, '' AS dsi,
                     '/system/notification-receivers' AS path, '' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
    UNION ALL SELECT 110230, 100123, 'RS3110230', '编辑通知接收人', 3, 1, '', '', '', '', '', 'system:notification-receiver:edit', 1, '', 'sys', 'sys'
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.id);

UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(`menu_ids`, '$', 100123), '$', 110230)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`) AND JSON_CONTAINS(`menu_ids`, '100084') AND NOT JSON_CONTAINS(`menu_ids`, '100123');

-- 给有「业务设置」分组下菜单的角色（如租户管理员、总经理）同样开放
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT rr.`role_code`, s.`id`, s.`code`
FROM `role_resource` rr
JOIN `resource` s ON s.`id` IN (100123, 110230)
WHERE rr.`resource_id` = 100094
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = rr.`role_code` AND y.`resource_id` = s.`id`);
