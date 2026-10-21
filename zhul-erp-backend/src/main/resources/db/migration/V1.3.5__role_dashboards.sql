-- 按角色的工作台看板（openspec role-dashboards）：
-- 1. 工作台模板表 workbench_template（所有公司共用）与七个内置模板，组件清单按显示顺序；
--    新增岗位看板 = 在这里插一行模板（选用已有组件），不改代码；
-- 2. 角色表加「工作台模板」字段：内置角色与公司已有的自建角色按名称预设一次，之后以角色管理为准；
-- 3. 去掉「采购管理 → 兼职看板」菜单，兼职采购改为进入工作台（采购员看板）。

CREATE TABLE `workbench_template` (
    `id`          int(11)      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`        varchar(32)  NOT NULL DEFAULT '' COMMENT '模板编码（boss、sales_lead、sales、purchase_lead、purchaser、ops_lead、general）',
    `name`        varchar(32)  NOT NULL DEFAULT '' COMMENT '模板名称',
    `seniority`   tinyint(2)   NOT NULL DEFAULT 9 COMMENT '职级排序，数字越小职级越高（一人多个模板时默认打开最高的）',
    `widgets`     json         NOT NULL COMMENT '看板组件清单（按显示顺序）：[{"key":组件编码,"size":"full-整行、half-半行"}]',
    `description` varchar(128) NOT NULL DEFAULT '' COMMENT '一句话说明（角色管理下拉里显示）',
    `sort`        int(11)      NOT NULL DEFAULT 0 COMMENT '列表顺序',
    `status`      tinyint(2)   NOT NULL DEFAULT 1 COMMENT '状态（0-停用、1-启用）',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作台模板：岗位看板由哪些组件组成';

INSERT INTO `workbench_template` (`code`, `name`, `seniority`, `widgets`, `description`, `sort`) VALUES
('boss', '老板', 1, '[{"key":"biz-overview","size":"full"},{"key":"cash","size":"half"},{"key":"sales-funnel","size":"half"},{"key":"revenue-trend","size":"half"},{"key":"risk-alerts","size":"half"},{"key":"rankings","size":"full"}]', '经营概览、资金、销售漏斗、风险提醒', 1),
('sales_lead', '业务负责人', 2, '[{"key":"team-performance","size":"full"},{"key":"follow-ups","size":"half"},{"key":"opportunity-stats","size":"half"},{"key":"sales-compare","size":"half"},{"key":"quote-conversion","size":"half"}]', '团队业绩、待跟进、业务员对比', 2),
('purchase_lead', '采购负责人', 2, '[{"key":"sourcing-overview","size":"full"},{"key":"buyer-efficiency","size":"half"},{"key":"purchase-execution","size":"half"},{"key":"bargain-result","size":"half"},{"key":"payables","size":"half"}]', '询价、采购员效率、采购执行', 3),
('ops_lead', '运营负责人', 2, '[{"key":"content-pipeline","size":"full"},{"key":"platform-progress","size":"half"},{"key":"media-overview","size":"half"},{"key":"monthly-output","size":"full"}]', '内容流水线、平台发布进度、素材', 4),
('sales', '业务员', 3, '[{"key":"my-todo","size":"half"},{"key":"my-performance","size":"half"},{"key":"quote-progress","size":"half"},{"key":"overdue-pi","size":"half"},{"key":"my-opportunities","size":"half"}]', '我的待办、我的业绩、询价进度', 5),
('purchaser', '采购员', 3, '[{"key":"my-inquiry","size":"full"},{"key":"quote-stats","size":"half"},{"key":"quote-trend","size":"half"},{"key":"my-purchase","size":"full"}]', '我的询价、回价统计、我的采购', 6),
('general', '通用', 9, '[{"key":"opportunity-stats","size":"full"},{"key":"overdue-pi","size":"full"}]', '按权限显示的卡片与常用入口', 7);

-- ---------------------------------------------------------------- 角色的工作台模板

ALTER TABLE `role` ADD COLUMN `workbench_template` varchar(32) NOT NULL DEFAULT 'general'
    COMMENT '工作台模板编码（关联 workbench_template.code）' AFTER `permission_scope`;

UPDATE `role` SET `workbench_template` = 'boss' WHERE `code` = 'ROLE_ADMIN';
UPDATE `role` SET `workbench_template` = 'purchaser' WHERE `code` = 'ROLE_PTBUYER';

-- 公司已有的自建角色按名称预设一次（先匹配负责人，再匹配员工）
UPDATE `role` SET `workbench_template` = CASE
    WHEN `name` LIKE '%总经理%' OR `name` LIKE '%老板%' THEN 'boss'
    WHEN `name` LIKE '%业务主管%' OR `name` LIKE '%业务负责人%' OR `name` LIKE '%销售主管%' THEN 'sales_lead'
    WHEN `name` LIKE '%采购主管%' OR `name` LIKE '%采购负责人%' THEN 'purchase_lead'
    WHEN `name` LIKE '%运营主管%' OR `name` LIKE '%运营负责人%' THEN 'ops_lead'
    WHEN `name` LIKE '%业务员%' OR `name` LIKE '%销售%' THEN 'sales'
    WHEN `name` LIKE '%采购员%' OR `name` LIKE '%采购%' THEN 'purchaser'
    ELSE 'general' END
WHERE `is_built_in` = 0;

-- ---------------------------------------------------------------- 去掉兼职看板

-- 兼职采购补上工作台菜单（登录后进入工作台的采购员看板）
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` = 100001
WHERE r.`code` = 'ROLE_PTBUYER'
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);

DELETE FROM `role_resource` WHERE `resource_id` = 100057;
UPDATE `tenant_package` SET `menu_ids` = (
    SELECT JSON_ARRAYAGG(x.`id`) FROM JSON_TABLE(`tenant_package`.`menu_ids`, '$[*]' COLUMNS (`id` INT PATH '$')) x
    WHERE x.`id` <> 100057)
WHERE JSON_CONTAINS(`menu_ids`, '100057');
UPDATE `resource` SET `status` = 0, `is_hidden` = 1, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100057;
