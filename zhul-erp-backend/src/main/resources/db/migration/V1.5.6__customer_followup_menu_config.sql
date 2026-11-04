-- 客户跟进（openspec change customer-followup）：系统参数、菜单与按钮权限
-- 1. 系统参数 followup.*（平台模板 tenant_id = 0，租户可覆盖，design D6、D7）
-- 2. 菜单「销售管理 / 客户跟进」，排在商机管理之前（spec system/navigation）
-- 3. 按钮权限「记跟进」「查看团队」
--    - 菜单与「记跟进」授给有商机管理菜单的角色（业务员、事业部合伙人、总经理等）
--    - 「查看团队」只授给其中数据权限为全部或自定义的角色（不授给仅本人的业务员）

-- ---------------------------------------------------------------- 系统参数
INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_type`, `is_builtin`, `config_group`, `remark`, `create_by`, `update_by`)
SELECT 0, k.config_key, k.config_name, k.config_value, k.config_type, 1, 'sales', k.remark, 'sys', 'sys'
FROM (
              SELECT 'sales.followup.quote.days' AS config_key, '客户跟进：报价后提醒天数' AS config_name, '1,3,7,14' AS config_value,
                     'STRING' AS config_type, '报价发送后第几天提醒跟进，逗号分隔、从小到大；最后一个节点之后不再提醒，改为建议转培育' AS remark
    UNION ALL SELECT 'sales.followup.pi.interval-days', '客户跟进：PI 后提醒间隔（天）', '2', 'INTEGER',
                     'PI 发出后没有预计付款日时每隔几天提醒；预计付款日过了之后也按这个间隔顺延'
    UNION ALL SELECT 'sales.followup.nurture.default-days', '客户跟进：培育默认天数', '60', 'INTEGER',
                     '转培育时默认多少天后重新激活，业务员可改'
    UNION ALL SELECT 'sales.followup.nurture.quote-days', '客户跟进：报价后建议转培育天数', '14', 'INTEGER',
                     '报价发送超过这么多天，列表提示「建议转培育」'
    UNION ALL SELECT 'sales.followup.nurture.no-reply-times', '客户跟进：连续没回复几次建议转培育', '3', 'INTEGER',
                     '连续记「没回复」达到这个次数，列表提示「建议转培育」'
    UNION ALL SELECT 'sales.followup.amount-tiers', '客户跟进：金额分档（元）', '200000,50000,10000', 'STRING',
                     '排序用的本位币金额分档阈值，从高到低逗号分隔：20 万以上、5–20 万、1–5 万、1 万以下'
) k
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` c WHERE c.`tenant_id` = 0 AND c.`config_key` = k.config_key);

-- ---------------------------------------------------------------- 菜单与按钮
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT 110226, 100081, 'RS2110226', '客户跟进', 2, 0, 'schedule', '', '', '', '/sales/followup', '', 1, '', 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = 110226);

INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT 110227, 110226, 'RS3110227', '记跟进', 3, 1, '', '', '', '', '', 'crm:followup:record', 1, '', 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = 110227);

INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT 110228, 110226, 'RS3110228', '查看团队', 3, 2, '', '', '', '', '', 'crm:followup:team', 1, '', 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = 110228);

UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(`menu_ids`, '$', 110226), '$', 110227), '$', 110228)
WHERE JSON_CONTAINS(`menu_ids`, '100053') AND NOT JSON_CONTAINS(`menu_ids`, '110226');

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, 110226, 'RS2110226' FROM `role_resource` x
WHERE x.`resource_id` = 100053
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = 110226);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, 110227, 'RS3110227' FROM `role_resource` x
WHERE x.`resource_id` = 100053
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = 110227);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, 110228, 'RS3110228' FROM `role_resource` x
JOIN `role` r ON r.`code` = x.`role_code`
WHERE x.`resource_id` = 100053 AND r.`permission_scope` IN (1, 2)
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = 110228);

-- 回退：
-- DELETE FROM `role_resource` WHERE `resource_id` IN (110226, 110227, 110228);
-- DELETE FROM `resource` WHERE `id` IN (110226, 110227, 110228);
-- tenant_package.menu_ids 中多出的 110226–110228 无对应菜单，不影响使用，可保留
-- DELETE FROM `sys_config` WHERE `tenant_id` = 0 AND `config_key` LIKE 'followup.%';
