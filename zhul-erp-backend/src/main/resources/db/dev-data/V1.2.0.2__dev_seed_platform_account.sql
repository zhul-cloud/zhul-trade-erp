-- V1.2.0.2：平台账号（tenant_id=0，仅用于本地快速体验/联调，生产环境请自行创建并删除）
-- 商品主数据表 tenant_id 固定为 0（全租户共享），写接口要求 PlatformScopeGuard
-- 判定 JWT tenantId=0，租户账号（如 admin）即使分配了 V1.2.0.1 里的按钮权限也只能读、
-- 不能写，所以额外建一个 tenant_id=0 的账号用于联调和体验。登录：platform / admin123
INSERT INTO `department` (`id`, `tenant_id`, `pid`, `code`, `name`, `all_name`, `sort`, `status`, `create_by`, `update_by`) VALUES
(19000, 0, 0, 'DP19000', '平台', '平台', 1, 1, 'sys', 'sys');

INSERT INTO `position` (`id`, `tenant_id`, `code`, `name`, `sort`, `status`, `create_by`, `update_by`) VALUES
(19000, 0, 'POS19000', '平台管理员', 1, 1, 'sys', 'sys');

INSERT INTO `user_basic` (`id`, `tenant_id`, `pid`, `name`, `type`, `username`, `phone`, `dept_id`, `position_id`, `role_code`, `email`, `nickname`, `status`, `create_by`, `update_by`) VALUES
(1000009000, 0, 0, '平台', 1, 'platform', '13900000000', 19000, 19000, 'ROLE_SYSADM', 'platform@example.com', 'Platform', 1, 'sys', 'sys');

INSERT INTO `account` (`id`, `tenant_id`, `user_id`, `username`, `phone`, `email`, `admin_flag`, `login_status`, `status`, `create_by`, `update_by`) VALUES
(10009000, 0, 1000009000, 'platform', '13900000000', 'platform@example.com', 1, 0, 1, 'sys', 'sys');

-- 密码 admin123 的 BCrypt 哈希，与 admin 账号复用同一条哈希（同一份明文密码）
INSERT INTO `account_local_auth` (`account_id`, `username`, `password`, `salt`, `create_by`, `update_by`) VALUES
(10009000, 'platform', '$2a$10$j7sZ9u5D7vUyr6m5MpUKQOQ1heKoS/MAu2ONRxlyL7uL944TDOlSO', '', 'sys', 'sys');
