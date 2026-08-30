USE zhul_erp;

-- 清理旧数据（仅开发用）
DELETE FROM `resource` WHERE id >= 100000;

-- 顶级菜单
INSERT INTO `resource` (`id`, `pid`, `code`, `name`, `type`, `sort`, `light_icon`, `light_selected_icon`, `dark_icon`, `dark_selected_icon`, `path`, `status`, `micro_app`, `create_by`, `update_by`) VALUES
(100001, 0, 'RS2100001', '工作台',   2, 1,  'dashboard', '', '', '', '/dashboard', 1, '', 'sys', 'sys'),
(100002, 0, 'RS1100002', '系统管理', 1, 2,  'setting',   '', '', '', '/system',    1, '', 'sys', 'sys'),
(100003, 0, 'RS1100003', '租户管理', 1, 99, 'cluster',   '', '', '', '/tenant',    1, '', 'sys', 'sys');

-- 系统管理子菜单
INSERT INTO `resource` (`id`, `pid`, `code`, `name`, `type`, `sort`, `light_icon`, `light_selected_icon`, `dark_icon`, `dark_selected_icon`, `path`, `status`, `micro_app`, `create_by`, `update_by`) VALUES
(100011, 100002, 'RS2100011', '用户管理', 2, 1, 'user',      '', '', '', '/system/user',        1, '', 'sys', 'sys'),
(100012, 100002, 'RS2100012', '角色管理', 2, 2, 'team',      '', '', '', '/system/role',        1, '', 'sys', 'sys'),
(100013, 100002, 'RS2100013', '菜单管理', 2, 3, 'menu',      '', '', '', '/system/menu',        1, '', 'sys', 'sys'),
(100014, 100002, 'RS2100014', '部门管理', 2, 4, 'apartment', '', '', '', '/system/dept',        1, '', 'sys', 'sys'),
(100015, 100002, 'RS2100015', '岗位管理', 2, 5, 'idcard',    '', '', '', '/system/position',    1, '', 'sys', 'sys'),
(100016, 100002, 'RS2100016', '字典管理', 2, 6, 'book',      '', '', '', '/system/dict',        1, '', 'sys', 'sys'),
(100017, 100002, 'RS2100017', '系统设置', 2, 7, 'tool',      '', '', '', '/system/config',      1, '', 'sys', 'sys'),
(100018, 100002, 'RS2100018', '操作日志', 2, 8, 'fileText',  '', '', '', '/system/log/operate', 1, '', 'sys', 'sys'),
(100019, 100002, 'RS2100019', '登录日志', 2, 9, 'login',     '', '', '', '/system/log/login',   1, '', 'sys', 'sys');

-- 租户管理子菜单
INSERT INTO `resource` (`id`, `pid`, `code`, `name`, `type`, `sort`, `light_icon`, `light_selected_icon`, `dark_icon`, `dark_selected_icon`, `path`, `status`, `micro_app`, `create_by`, `update_by`) VALUES
(100021, 100003, 'RS2100021', '租户列表', 2, 1, 'cluster',  '', '', '', '/tenant/list',    1, '', 'sys', 'sys'),
(100022, 100003, 'RS2100022', '套餐管理', 2, 2, 'appstore', '', '', '', '/tenant/package', 1, '', 'sys', 'sys');

-- ===========================
-- 按钮权限（type=3）
-- 权限码存 permission 字段（path 留空，路由路径和权限标识是两个独立字段）
-- ===========================

-- 用户管理按钮（pid=100011）
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`) VALUES
(110001, 100011, 'RS3110001', '新增用户', 3, 1, '','','','', '', 'system:user:add',      1,'','sys','sys'),
(110002, 100011, 'RS3110002', '编辑用户', 3, 2, '','','','', '', 'system:user:edit',     1,'','sys','sys'),
(110003, 100011, 'RS3110003', '删除用户', 3, 3, '','','','', '', 'system:user:delete',   1,'','sys','sys'),
(110004, 100011, 'RS3110004', '重置密码', 3, 4, '','','','', '', 'system:user:resetPwd', 1,'','sys','sys'),
(110005, 100011, 'RS3110005', '启用禁用', 3, 5, '','','','', '', 'system:user:status',   1,'','sys','sys');

-- 角色管理按钮（pid=100012）
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`) VALUES
(110011, 100012, 'RS3110011', '新增角色', 3, 1, '','','','', '', 'system:role:add',    1,'','sys','sys'),
(110012, 100012, 'RS3110012', '编辑角色', 3, 2, '','','','', '', 'system:role:edit',   1,'','sys','sys'),
(110013, 100012, 'RS3110013', '删除角色', 3, 3, '','','','', '', 'system:role:delete', 1,'','sys','sys'),
(110014, 100012, 'RS3110014', '分配权限', 3, 4, '','','','', '', 'system:role:assign', 1,'','sys','sys');

-- 菜单管理按钮（pid=100013）
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`) VALUES
(110021, 100013, 'RS3110021', '新增菜单', 3, 1, '','','','', '', 'system:menu:add',    1,'','sys','sys'),
(110022, 100013, 'RS3110022', '编辑菜单', 3, 2, '','','','', '', 'system:menu:edit',   1,'','sys','sys'),
(110023, 100013, 'RS3110023', '删除菜单', 3, 3, '','','','', '', 'system:menu:delete', 1,'','sys','sys');

-- 部门管理按钮（pid=100014）
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`) VALUES
(110031, 100014, 'RS3110031', '新增部门', 3, 1, '','','','', '', 'system:dept:add',    1,'','sys','sys'),
(110032, 100014, 'RS3110032', '编辑部门', 3, 2, '','','','', '', 'system:dept:edit',   1,'','sys','sys'),
(110033, 100014, 'RS3110033', '删除部门', 3, 3, '','','','', '', 'system:dept:delete', 1,'','sys','sys');

-- 岗位管理按钮（pid=100015）
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`) VALUES
(110041, 100015, 'RS3110041', '新增岗位', 3, 1, '','','','', '', 'system:position:add',    1,'','sys','sys'),
(110042, 100015, 'RS3110042', '编辑岗位', 3, 2, '','','','', '', 'system:position:edit',   1,'','sys','sys'),
(110043, 100015, 'RS3110043', '删除岗位', 3, 3, '','','','', '', 'system:position:delete', 1,'','sys','sys');

-- 字典管理按钮（pid=100016）
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`) VALUES
(110051, 100016, 'RS3110051', '新增字典', 3, 1, '','','','', '', 'system:dict:add',    1,'','sys','sys'),
(110052, 100016, 'RS3110052', '编辑字典', 3, 2, '','','','', '', 'system:dict:edit',   1,'','sys','sys'),
(110053, 100016, 'RS3110053', '删除字典', 3, 3, '','','','', '', 'system:dict:delete', 1,'','sys','sys');

-- 系统设置按钮（pid=100017）
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`) VALUES
(110071, 100017, 'RS3110071', '新增配置', 3, 1, '','','','', '', 'system:config:add',    1,'','sys','sys'),
(110072, 100017, 'RS3110072', '编辑配置', 3, 2, '','','','', '', 'system:config:edit',   1,'','sys','sys'),
(110073, 100017, 'RS3110073', '删除配置', 3, 3, '','','','', '', 'system:config:delete', 1,'','sys','sys');

-- 租户管理按钮（pid=100021）
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`) VALUES
(110061, 100021, 'RS3110061', '新增租户', 3, 1, '','','','', '', 'tenant:list:add',      1,'','sys','sys'),
(110062, 100021, 'RS3110062', '编辑租户', 3, 2, '','','','', '', 'tenant:list:edit',     1,'','sys','sys'),
(110063, 100021, 'RS3110063', '禁用启用', 3, 3, '','','','', '', 'tenant:list:status',   1,'','sys','sys'),
(110064, 100021, 'RS3110064', '重置密码', 3, 4, '','','','', '', 'tenant:list:resetPwd', 1,'','sys','sys');

-- 登录日志按钮（pid=100019）
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`) VALUES
(110081, 100019, 'RS3110081', '强制下线', 3, 1, '','','','', '', 'system:log:login:forceLogout', 1,'','sys','sys');

-- ===========================
-- 内置角色（不可删除）
-- ===========================
INSERT INTO `role` (`tenant_id`, `code`, `name`, `permission_scope`, `status`, `is_built_in`, `remark`, `create_by`, `update_by`) VALUES
(0, 'ROLE_ADMIN',   '租户管理员', 1, 1, 1, '内置角色，拥有租户内全部数据权限', 'sys', 'sys'),
(0, 'ROLE_SYSADM',  '系统管理员', 1, 1, 1, '内置角色，负责系统配置与权限管理', 'sys', 'sys'),
(0, 'ROLE_STAFF',   '普通员工',   3, 1, 1, '内置角色，仅可查看本人数据', 'sys', 'sys');

-- ===========================
-- 内置字典类型（不可删除）
-- ===========================
INSERT INTO `dict_type` (`tenant_id`, `dict_type`, `dict_name`, `is_builtin`, `status`, `remark`, `create_by`, `update_by`) VALUES
(0, 'sys_user_status',    '用户状态', 1, 1, '用户账号启用/禁用状态', 'sys', 'sys'),
(0, 'sys_order_status',   '订单状态', 1, 1, '订单业务流转状态', 'sys', 'sys'),
(0, 'sys_currency_type',  '货币类型', 1, 1, '系统支持的币种', 'sys', 'sys'),
(0, 'sys_payment_method', '付款方式', 1, 1, '外贸收付款方式', 'sys', 'sys');

INSERT INTO `dict_item` (`tenant_id`, `dict_type_id`, `dict_type`, `item_code`, `item_name`, `item_value`, `css_class`, `sort_order`, `is_default`, `status`, `create_by`, `update_by`)
SELECT 0, id, dict_type, 'ENABLED', '启用', '1', 'success', 1, 1, 1, 'sys', 'sys' FROM `dict_type` WHERE dict_type = 'sys_user_status';
INSERT INTO `dict_item` (`tenant_id`, `dict_type_id`, `dict_type`, `item_code`, `item_name`, `item_value`, `css_class`, `sort_order`, `is_default`, `status`, `create_by`, `update_by`)
SELECT 0, id, dict_type, 'DISABLED', '禁用', '0', 'default', 2, 0, 1, 'sys', 'sys' FROM `dict_type` WHERE dict_type = 'sys_user_status';

-- ===========================
-- 示例租户 & 初始管理员账号（仅用于本地快速体验，生产环境请自行创建并删除）
-- 登录：admin / admin123
-- ===========================
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
