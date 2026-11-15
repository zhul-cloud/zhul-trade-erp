-- 客户跟进菜单与参数 V1.5.6 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.5.6 的记录）。
-- 以本文件为准：迁移文件末尾注释里的 LIKE 'followup.%' 匹配不到本迁移插入的参数（实际键名都以 sales.followup. 开头，audit OPS-009），
-- 已执行的迁移不能改动，所以回退语句放在这里。后续版本（V1.5.9、V1.5.11、V1.5.13）也插入了 sales.followup.* 参数，
-- 这里按键名逐个删除本迁移插入的 6 个，不用前缀匹配。
-- tenant_package.menu_ids 中多出的 110226–110228 无对应菜单，不影响使用，可保留。
DELETE FROM `role_resource` WHERE `resource_id` IN (110226, 110227, 110228);
DELETE FROM `resource` WHERE `id` IN (110226, 110227, 110228);
DELETE FROM `sys_config` WHERE `tenant_id` = 0 AND `config_key` IN ('sales.followup.quote.days', 'sales.followup.pi.interval-days', 'sales.followup.nurture.default-days', 'sales.followup.nurture.quote-days', 'sales.followup.nurture.no-reply-times', 'sales.followup.amount-tiers');
