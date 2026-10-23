-- 删除「系统管理员」角色 V1.3.12 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.3.12 的记录）。
-- 恢复为删除前的停用状态（V1.2.4 下线后的样子）；该角色删除前没有用户、权限与自定义部门，无需恢复其他数据。

INSERT INTO `role` (`id`, `tenant_id`, `code`, `name`, `permission_scope`, `workbench_template`, `status`, `is_built_in`, `remark`, `create_by`, `update_by`)
SELECT 101, 0, 'ROLE_SYSADM', '系统管理员', 1, 'general', 0, 1,
       '已下线：平台超管身份由 account.admin_flag + tenant_id=0 判断，不依赖角色；统一使用 ROLE_ADMIN（租户管理员）', 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `role` WHERE `code` = 'ROLE_SYSADM');
