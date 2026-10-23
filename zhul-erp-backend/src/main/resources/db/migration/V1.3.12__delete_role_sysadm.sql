-- 删除已下线的内置角色「系统管理员」（ROLE_SYSADM）：V1.2.4 已停用并把用户迁到租户管理员，
-- 平台超管由 account.admin_flag + tenant_id=0 判断，不依赖角色；留着停用的角色容易和「租户管理员」混淆，直接删除。
-- 角色表是基础表，与角色管理页删除角色一致为物理删除。为防万一，仍在用它的用户先改为租户管理员。可重复执行。

UPDATE `user_basic` SET `role_code` = 'ROLE_ADMIN', `update_time` = NOW(), `update_by` = 'sys' WHERE `role_code` = 'ROLE_SYSADM';
DELETE FROM `account_role` WHERE `role_code` = 'ROLE_SYSADM';
DELETE FROM `role_resource` WHERE `role_code` = 'ROLE_SYSADM';
DELETE FROM `role_org` WHERE `role_code` = 'ROLE_SYSADM';
DELETE FROM `role` WHERE `code` = 'ROLE_SYSADM';
