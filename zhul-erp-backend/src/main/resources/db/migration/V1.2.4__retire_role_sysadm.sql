-- 下线内置角色"系统管理员"（ROLE_SYSADM）：
-- 平台超管身份已经由 account.admin_flag=1 + tenant_id=0 判断，不依赖角色码；
-- ROLE_SYSADM 在 role_resource 里从未配置过任何权限，跟"租户管理员"（ROLE_ADMIN）
-- 功能上完全重合、名字却容易让人误会两者是不同层级的管理员，故统一收敛到 ROLE_ADMIN。
-- 角色表是基础表（同 resource/dict 表），不加 deleted_at，用 status 禁用即可。

UPDATE `user_basic` SET `role_code` = 'ROLE_ADMIN', `update_by` = 'sys'
WHERE `role_code` = 'ROLE_SYSADM';

DELETE FROM `role_resource` WHERE `role_code` = 'ROLE_SYSADM';

UPDATE `role`
SET `status` = 0,
    `remark` = '已下线：平台超管身份由 account.admin_flag + tenant_id=0 判断，不依赖角色；统一使用 ROLE_ADMIN（租户管理员）',
    `update_by` = 'sys'
WHERE `code` = 'ROLE_SYSADM';
