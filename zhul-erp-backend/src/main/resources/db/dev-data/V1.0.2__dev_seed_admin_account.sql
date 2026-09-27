-- V1.0.2：示例租户 & 初始管理员账号
-- 仅用于本地快速体验/联调，生产环境不要启用这个 location（见 application.yml 里
-- spring.flyway.locations 只在 application-dev.yml 才加 classpath:db/dev-data）。
-- 登录：admin / admin123
INSERT INTO `tenant_package` (`id`, `name`, `menu_ids`, `remark`, `status`, `create_by`, `update_by`) VALUES
(1, '标准版', '[]', '默认套餐', 1, 'sys', 'sys');

INSERT INTO `tenant` (`id`, `code`, `name`, `package_id`, `contact_name`, `contact_phone`, `status`, `create_by`, `update_by`) VALUES
(1000, 'TN1000', '示例科技有限公司', 1, '系统管理员', '13800000000', 1, 'sys', 'sys');

INSERT INTO `department` (`id`, `tenant_id`, `pid`, `code`, `name`, `all_name`, `sort`, `status`, `create_by`, `update_by`) VALUES
(10001, 1000, 0, 'DP10001', '总经办', '总经办', 1, 1, 'sys', 'sys');

INSERT INTO `position` (`id`, `tenant_id`, `code`, `name`, `sort`, `status`, `create_by`, `update_by`) VALUES
(10000, 1000, 'POS10000', '系统管理员', 1, 1, 'sys', 'sys');

INSERT INTO `user_basic` (`id`, `tenant_id`, `pid`, `name`, `type`, `username`, `phone`, `dept_id`, `position_id`, `role_code`, `email`, `nickname`, `status`, `create_by`, `update_by`) VALUES
(1000000000, 1000, 0, '无名氏', 1, 'admin', '13800000000', 10001, 10000, 'ROLE_SYSADM', 'admin@example.com', 'Admin', 1, 'sys', 'sys');

INSERT INTO `account` (`id`, `tenant_id`, `user_id`, `username`, `phone`, `email`, `admin_flag`, `login_status`, `status`, `create_by`, `update_by`) VALUES
(10000000, 1000, 1000000000, 'admin', '13800000000', 'admin@example.com', 1, 0, 1, 'sys', 'sys');

-- 密码 admin123 的 BCrypt 哈希（仅示例账号使用，生产环境务必修改密码）
INSERT INTO `account_local_auth` (`account_id`, `username`, `password`, `salt`, `create_by`, `update_by`) VALUES
(10000000, 'admin', '$2a$10$j7sZ9u5D7vUyr6m5MpUKQOQ1heKoS/MAu2ONRxlyL7uL944TDOlSO', '', 'sys', 'sys');
