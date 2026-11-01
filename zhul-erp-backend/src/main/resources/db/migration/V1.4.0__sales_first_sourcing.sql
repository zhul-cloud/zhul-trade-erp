-- 业务员优先询价（openspec/changes/sales-first-sourcing）：
-- 1. 询价任务先进「待业务处理」，询盘负责人认领或转采购，超时自动进入待分配；
-- 2. 型号增加询价轮次，按型号退回后本轮有结果才算回价；
-- 3. 转出记录用于按人员统计工作量；
-- 4. 采购人员改按「可接询价任务」权限认定，不再按「我的询价」菜单。

ALTER TABLE `sourcing_task`
    MODIFY COLUMN `status` tinyint(2) NOT NULL DEFAULT 1 COMMENT '状态（1-待分配、2-询价中、3-已回价、4-已取消、6-待业务处理）',
    ADD COLUMN `sales_deadline` datetime NULL COMMENT '待业务处理的流转时间，到期自动进入待分配',
    ADD COLUMN `pool_entered_at` datetime NULL COMMENT '最近一次进入待分配的时间，等待时长从这里算',
    ADD COLUMN `sales_handling` tinyint(2) NOT NULL DEFAULT 0 COMMENT '是否业务员自询中（0-否、1-是）',
    ADD KEY `idx_sales_deadline` (`sales_deadline`);

ALTER TABLE `sourcing_task_assignee`
    MODIFY COLUMN `assign_mode` tinyint(2) NOT NULL DEFAULT 1 COMMENT '分配方式（1-手动、2-按推荐、3-按规则、4-追加比价、5-改派、6-业务员自询）';

ALTER TABLE `inquiry_item`
    ADD COLUMN `quote_round` int NOT NULL DEFAULT 0 COMMENT '询价轮次，按型号退回时加一；本轮有已提交记录才算回价';

ALTER TABLE `sourcing_quote`
    ADD COLUMN `quote_round` int NOT NULL DEFAULT 0 COMMENT '提交时型号所在的询价轮次';

CREATE TABLE `sourcing_item_handoff` (
    `id`              bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`       int(11)     NOT NULL DEFAULT 0  COMMENT '租户ID',
    `inquiry_item_id` bigint      NOT NULL DEFAULT 0  COMMENT '型号ID，关联inquiry_item.id',
    `task_id`         bigint      NOT NULL DEFAULT 0  COMMENT '询价任务ID，关联sourcing_task.id',
    `from_user_id`    bigint      NOT NULL DEFAULT 0  COMMENT '转出人ID，关联user_basic.id',
    `quote_round`     int         NOT NULL DEFAULT 0  COMMENT '转出时型号所在的询价轮次',
    `reason`          tinyint(2)  NOT NULL DEFAULT 0  COMMENT '退回原因（1-型号存疑、2-停产无货、3-超出能力、9-其他）',
    `deleted_at`      datetime    NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`     datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`       varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`     datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`       varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_inquiry_item_id` (`inquiry_item_id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_from_user_id` (`from_user_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='询价型号转出记录';

-- 按钮权限「可接询价任务」，挂在我的询价下
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
    SELECT 110219 AS id, 100055 AS pid, 'RS3110219' AS code, '可接询价任务' AS name, 3 AS type, 1 AS sort, '' AS li,
           '' AS lsi, '' AS di, '' AS dsi, '' AS path, 'inquiry:sourcing:accept' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.id);

UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 110219)
WHERE JSON_CONTAINS(`menu_ids`, '100055') AND NOT JSON_CONTAINS(`menu_ids`, '110219');

-- 有「我的询价」菜单、但没有客户询盘菜单的角色视为采购（含内置的兼职采购）。
-- 能看客户询盘的角色是业务员（约定见 V1.2.26），不授权；既做业务又做采购的角色由管理员手动勾选。
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT rr.`role_code`, 110219, 'RS3110219'
FROM `role_resource` rr
WHERE rr.`resource_id` = 100055
  AND NOT EXISTS (SELECT 1 FROM `role_resource` c WHERE c.`role_code` = rr.`role_code` AND c.`resource_id` = 100051)
  AND NOT EXISTS (SELECT 1 FROM `role_resource` x WHERE x.`role_code` = rr.`role_code` AND x.`resource_id` = 110219);

-- 系统设置（平台模板，租户可覆盖）
INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_type`, `is_builtin`, `config_group`, `remark`, `create_by`, `update_by`)
SELECT 0, k.config_key, k.config_name, k.config_value, k.config_type, 1, 'inquiry', k.remark, 'sys', 'sys'
FROM (
    SELECT 'inquiry.sourcing.sales-window-minutes' AS config_key, '业务员处理询价任务的时限（分钟）' AS config_name, '120' AS config_value, 'INTEGER' AS config_type, '询盘确认后业务员可认领的时长，到期自动转给采购；0 表示直接进入待分配' AS remark
    UNION ALL SELECT 'inquiry.sourcing.sales-window-urgent-minutes', '业务员处理紧急询价任务的时限（分钟）', '30', 'INTEGER', '紧急询盘确认后业务员可认领的时长，到期自动转给采购；0 表示直接进入待分配'
) k
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` c WHERE c.`tenant_id` = 0 AND c.`config_key` = k.config_key);
