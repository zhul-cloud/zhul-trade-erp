-- ===========================
-- 兼职回价审核：兼职采购提交的回价先进入「待审核」，采购负责人选推荐、作废或退回后才对业务员可见
-- 存量兼职回价视为已审核通过，不迁移
-- ===========================
ALTER TABLE `sourcing_quote`
    MODIFY COLUMN `status` tinyint(2) NOT NULL DEFAULT 1 COMMENT '状态（1-草稿、2-已提交、3-待审核、4-已作废）',
    ADD COLUMN `review_note` varchar(200) NOT NULL DEFAULT '' COMMENT '审核说明：退回原因或作废原因' AFTER `submit_batch`,
    ADD COLUMN `reviewed_by` bigint NULL COMMENT '审核人用户ID' AFTER `review_note`,
    ADD COLUMN `reviewed_at` datetime NULL COMMENT '审核时间' AFTER `reviewed_by`;

-- 按钮权限「审核兼职回价」，挂在分配工作台下
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
    SELECT 110175 AS id, 100054 AS pid, 'RS3110175' AS code, '审核兼职回价' AS name, 3 AS type, 4 AS sort, '' AS li,
           '' AS lsi, '' AS di, '' AS dsi, '' AS path, 'inquiry:quote:review' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.id);

UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 110175)
WHERE JSON_CONTAINS(`menu_ids`, '100054') AND NOT JSON_CONTAINS(`menu_ids`, '110175');

-- 只给内置角色「租户管理员」；采购负责人等租户自建角色由管理员在角色管理中勾选
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT r.`code`, 110175, 'RS3110175'
FROM `role` r
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` x WHERE x.`role_code` = r.`code` AND x.`resource_id` = 110175);
