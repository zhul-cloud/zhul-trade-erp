drop schema if exists zhul_erp;
create schema zhul_erp default character set utf8mb4 collate utf8mb4_general_ci;
use zhul_erp;

-- ============================
-- v1.0.0  系统基础 & 用户域
-- 共 20 张表
-- ============================

-- ----------------------------
-- 租户套餐表
-- ----------------------------
DROP TABLE IF EXISTS `tenant_package`;
CREATE TABLE `tenant_package`
(
    `id`          int(11)      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        varchar(64)  NOT NULL DEFAULT '' COMMENT '套餐名称',
    `menu_ids`    text                  COMMENT '可用菜单ID集合（JSON数组）',
    `remark`      varchar(256) NOT NULL DEFAULT '' COMMENT '备注',
    `status`      tinyint(2)   NOT NULL DEFAULT 1  COMMENT '状态（0-禁用、1-启用）',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '租户套餐表';

-- ----------------------------
-- 租户表
-- ----------------------------
DROP TABLE IF EXISTS `tenant`;
CREATE TABLE `tenant`
(
    `id`            int(11)      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`          varchar(20)  NOT NULL DEFAULT '' COMMENT '租户编码（TN+主键，如TN1000）',
    `name`          varchar(64)  NOT NULL DEFAULT '' COMMENT '租户名称',
    `package_id`    int(11)      NOT NULL DEFAULT 0  COMMENT '套餐ID',
    `contact_name`  varchar(32)  NOT NULL DEFAULT '' COMMENT '联系人姓名',
    `contact_phone` varchar(16)  NOT NULL DEFAULT '' COMMENT '联系人手机号',
    `expire_time`   datetime     NOT NULL DEFAULT '2099-12-31 23:59:59' COMMENT '到期时间',
    `status`        tinyint(2)   NOT NULL DEFAULT 1  COMMENT '状态（0-禁用、1-启用）',
    `remark`        varchar(256) NOT NULL DEFAULT '' COMMENT '备注',
    `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`     varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`     varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_package_id` (`package_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB AUTO_INCREMENT = 1000 DEFAULT CHARSET = utf8mb4 COMMENT = '租户表';

-- ----------------------------
-- 资源表（菜单/按钮）
-- ----------------------------
DROP TABLE IF EXISTS `resource`;
CREATE TABLE `resource`
(
    `id`                  int(11)      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `pid`                 int(11)      NOT NULL DEFAULT 0  COMMENT '父ID',
    `tenant_id`           int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID（0=平台级共享）',
    `code`                varchar(14)  NOT NULL DEFAULT '' COMMENT '资源编码（RS+类型+主键，如RS100000）',
    `name`                varchar(32)  NOT NULL DEFAULT '' COMMENT '资源名称',
    `type`                tinyint(2)   NOT NULL DEFAULT 0  COMMENT '资源类型（1-目录、2-菜单、3-按钮）',
    `sort`                int(11)      NOT NULL DEFAULT 0  COMMENT '排序',
    `light_icon`          varchar(164) NOT NULL DEFAULT '' COMMENT '浅色默认图标地址',
    `light_selected_icon` varchar(164) NOT NULL DEFAULT '' COMMENT '浅色选中状态图标地址',
    `dark_icon`           varchar(164) NOT NULL DEFAULT '' COMMENT '深色默认图标地址',
    `dark_selected_icon`  varchar(164) NOT NULL DEFAULT '' COMMENT '深色选中状态图标地址',
    `path`                varchar(164) NOT NULL DEFAULT '' COMMENT '路由路径（菜单类型必填）',
    `component_path`      varchar(256) NOT NULL DEFAULT '' COMMENT '组件路径（菜单类型必填）',
    `permission`          varchar(128) NOT NULL DEFAULT '' COMMENT '权限标识（按钮类型必填，格式：模块:资源:操作）',
    `status`              tinyint(2)   NOT NULL DEFAULT 1  COMMENT '状态（0-禁用、1-启用）',
    `micro_app`           varchar(32)  NOT NULL DEFAULT '' COMMENT '微应用标识（字典）',
    `is_external`         tinyint(2)   NOT NULL DEFAULT 0  COMMENT '是否外链（0-否、1-是）',
    `is_cache`            tinyint(2)   NOT NULL DEFAULT 1  COMMENT '是否缓存（0-否、1-是）',
    `is_hidden`           tinyint(2)   NOT NULL DEFAULT 0  COMMENT '是否隐藏（0-否、1-是）',
    `create_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`           varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`           varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_pid` (`pid`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_permission` (`permission`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB AUTO_INCREMENT = 100000 DEFAULT CHARSET = utf8mb4 COMMENT = '资源表';

-- ----------------------------
-- 角色表
-- ----------------------------
DROP TABLE IF EXISTS `role`;
CREATE TABLE `role`
(
    `id`               int(11)      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID（0=平台级共享）',
    `code`             varchar(12)  NOT NULL DEFAULT '' COMMENT '角色编码',
    `name`             varchar(32)  NOT NULL DEFAULT '' COMMENT '角色名称',
    `permission_scope` tinyint(1)   NOT NULL DEFAULT 0  COMMENT '数据权限范围（0-无权限、1-全部、2-自定义、3-仅本人）',
    `status`           tinyint(1)   NOT NULL DEFAULT 1  COMMENT '状态（0-禁用、1-启用）',
    `is_built_in`      tinyint(2)   NOT NULL DEFAULT 0  COMMENT '是否内置角色（0-否、1-是，内置角色不可删除）',
    `remark`           varchar(128) NOT NULL DEFAULT '' COMMENT '备注',
    `create_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`        varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`        varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB AUTO_INCREMENT = 100 DEFAULT CHARSET = utf8mb4 COMMENT = '角色表';

-- ----------------------------
-- 角色资源关联表
-- ----------------------------
DROP TABLE IF EXISTS `role_resource`;
CREATE TABLE `role_resource`
(
    `id`            int(11)     NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_code`     varchar(12) NOT NULL DEFAULT '' COMMENT '角色编码',
    `resource_id`   int(11)     NOT NULL DEFAULT 0  COMMENT '资源ID',
    `resource_code` varchar(14) NOT NULL DEFAULT '' COMMENT '资源编码',
    `create_time`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`     varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`     varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_role_code` (`role_code`),
    KEY `idx_resource_id` (`resource_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色资源表';

-- ----------------------------
-- 角色机构关联表
-- ----------------------------
DROP TABLE IF EXISTS `role_org`;
CREATE TABLE `role_org`
(
    `id`          int(11)     NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_code`   varchar(12) NOT NULL DEFAULT '' COMMENT '角色编码',
    `org_code`    varchar(12) NOT NULL DEFAULT '' COMMENT '机构编码',
    `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_role_code` (`role_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色机构表';

-- ----------------------------
-- 部门表
-- ----------------------------
DROP TABLE IF EXISTS `department`;
CREATE TABLE `department`
(
    `id`          int(11)     NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)     NOT NULL DEFAULT 0  COMMENT '租户ID',
    `pid`         int(11)     NOT NULL DEFAULT 0  COMMENT '上级部门ID',
    `code`        varchar(13) NOT NULL DEFAULT '' COMMENT '部门编码（DP+主键，如DP10000）',
    `name`        varchar(32) NOT NULL DEFAULT '' COMMENT '部门名称',
    `all_name`    varchar(64) NOT NULL DEFAULT '' COMMENT '部门全称',
    `leader_id`   int(11)     NOT NULL DEFAULT 0  COMMENT '部门负责人ID',
    `level`       tinyint(2)  NOT NULL DEFAULT 0  COMMENT '所处层级',
    `headcount`   int(11)     NOT NULL DEFAULT 0  COMMENT '当前部门人数',
    `phone`       varchar(20)  NOT NULL DEFAULT '' COMMENT '联系电话',
    `remark`      varchar(256) NOT NULL DEFAULT '' COMMENT '备注',
    `sort`        int(11)     NOT NULL DEFAULT 0  COMMENT '排序',
    `status`      tinyint(2)  NOT NULL DEFAULT 1  COMMENT '状态（0-禁用、1-启用）',
    `deleted_at`  datetime    NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_pid` (`pid`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000 DEFAULT CHARSET = utf8mb4 COMMENT = '部门表';

-- ----------------------------
-- 岗位表
-- ----------------------------
DROP TABLE IF EXISTS `position`;
CREATE TABLE `position`
(
    `id`          int(11)      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `code`        varchar(32)  NOT NULL DEFAULT '' COMMENT '岗位编码（手动填写，大写字母/数字/下划线，租户内唯一）',
    `name`        varchar(64)  NOT NULL DEFAULT '' COMMENT '岗位名称',
    `sort`        int(11)      NOT NULL DEFAULT 0  COMMENT '排序',
    `status`      tinyint(2)   NOT NULL DEFAULT 1  COMMENT '状态（0-禁用、1-启用）',
    `remark`      varchar(256) NOT NULL DEFAULT '' COMMENT '备注',
    `deleted_at`  datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000 DEFAULT CHARSET = utf8mb4 COMMENT = '岗位表';

-- ----------------------------
-- 用户基本信息表
-- ----------------------------
DROP TABLE IF EXISTS `user_basic`;
CREATE TABLE `user_basic`
(
    `id`          int(11)      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `pid`         int(11)      NOT NULL DEFAULT 0  COMMENT '上级领导ID',
    `name`        varchar(16)  NOT NULL DEFAULT '' COMMENT '用户姓名',
    `type`        tinyint(2)   NOT NULL DEFAULT 1  COMMENT '用户类型（1-员工）',
    `username`    varchar(20)  NOT NULL DEFAULT '' COMMENT '用户名',
    `phone`       varchar(16)  NOT NULL DEFAULT '' COMMENT '手机号',
    `dept_id`     int(11)      NOT NULL DEFAULT 0  COMMENT '主部门ID',
    `position_id` int(11)      NOT NULL DEFAULT 0  COMMENT '主岗位ID',
    `role_code`   varchar(12)  NOT NULL DEFAULT '' COMMENT '主角色编码',
    `email`       varchar(100) NOT NULL DEFAULT '' COMMENT '邮箱地址',
    `avatar_url`  varchar(164) NOT NULL DEFAULT '' COMMENT '头像地址',
    `nickname`    varchar(64)  NOT NULL DEFAULT '' COMMENT '昵称',
    `status`      tinyint(2)   NOT NULL DEFAULT 1  COMMENT '状态（0-禁用、1-启用）',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_dept_id` (`dept_id`),
    KEY `idx_position_id` (`position_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB AUTO_INCREMENT = 1000000000 DEFAULT CHARSET = utf8mb4 COMMENT = '用户基本信息表';

-- ----------------------------
-- 用户部门关联表
-- ----------------------------
DROP TABLE IF EXISTS `user_department`;
CREATE TABLE `user_department`
(
    `id`          int(11)     NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     int(11)     NOT NULL DEFAULT 0  COMMENT '用户ID',
    `dept_code`   varchar(13) NOT NULL DEFAULT '' COMMENT '部门编码',
    `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户部门表';

-- ----------------------------
-- 用户岗位关联表
-- ----------------------------
DROP TABLE IF EXISTS `user_position`;
CREATE TABLE `user_position`
(
    `id`          int(11)     NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     int(11)     NOT NULL DEFAULT 0  COMMENT '用户ID',
    `position_id` int(11)     NOT NULL DEFAULT 0  COMMENT '岗位ID',
    `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_position_id` (`position_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户岗位表';

-- ----------------------------
-- 账号表
-- ----------------------------
DROP TABLE IF EXISTS `account`;
CREATE TABLE `account`
(
    `id`               int(11)      NOT NULL AUTO_INCREMENT COMMENT '账号ID',
    `tenant_id`        int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID（0=平台级共享）',
    `user_id`          int(11)      NOT NULL DEFAULT 0  COMMENT '用户ID',
    `username`         varchar(20)  NOT NULL DEFAULT '' COMMENT '用户名',
    `phone`            varchar(16)  NOT NULL DEFAULT '' COMMENT '手机号',
    `email`            varchar(100) NOT NULL DEFAULT '' COMMENT '邮箱地址',
    `admin_flag`       tinyint(2)   NOT NULL DEFAULT 0  COMMENT '管理员标记（0-否、1-是）',
    `last_login_time`  varchar(19)  NOT NULL DEFAULT '' COMMENT '最近登录时间',
    `last_logout_time` varchar(19)  NOT NULL DEFAULT '' COMMENT '最后登出时间',
    `login_status`     tinyint(2)   NOT NULL DEFAULT 0  COMMENT '当前登录状态（0-登出、1-登录）',
    `status`           tinyint(2)   NOT NULL DEFAULT 1  COMMENT '状态（0-禁用、1-启用）',
    `create_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`        varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`        varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000000 DEFAULT CHARSET = utf8mb4 COMMENT = '账号表';

-- ----------------------------
-- 账号本地认证表（密码）
-- ----------------------------
DROP TABLE IF EXISTS `account_local_auth`;
CREATE TABLE `account_local_auth`
(
    `id`          int(11)      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `account_id`  int(11)      NOT NULL DEFAULT 0  COMMENT '账号ID',
    `username`    varchar(20)  NOT NULL DEFAULT '' COMMENT '用户名（冗余）',
    `password`    varchar(128) NOT NULL DEFAULT '' COMMENT '密码（加密存储）',
    `salt`        varchar(32)  NOT NULL DEFAULT '' COMMENT '盐值',
    `fail_count`  int(11)      NOT NULL DEFAULT 0  COMMENT '连续登录失败次数，成功登录后清零',
    `last_fail_at` datetime    NULL                COMMENT '最后一次失败时间',
    `unlock_at`   datetime     NULL                COMMENT '锁定解除时间，NULL或早于当前时间表示未锁定',
    `reset_token` varchar(64)  NULL                COMMENT '找回密码验证码（6位数字）',
    `reset_token_expires_at` datetime NULL         COMMENT '验证码过期时间，10分钟有效',
    `reset_fail_count` int(11) NOT NULL DEFAULT 0  COMMENT '当前验证码连续验证失败次数，达到3次作废',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_account_id` (`account_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '账号本地认证表';

-- ----------------------------
-- 账号访问Token表
-- ----------------------------
DROP TABLE IF EXISTS `account_access_token`;
CREATE TABLE `account_access_token`
(
    `id`           int(11)      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`      int(11)      NOT NULL DEFAULT 0                   COMMENT '用户ID',
    `account_id`   int(11)      NOT NULL DEFAULT 0                   COMMENT '账号ID',
    `access_token` varchar(512) NOT NULL DEFAULT ''                  COMMENT '认证Token（JWT）',
    `refresh_token` varchar(512) NULL                                COMMENT 'RefreshToken，仅记住我时签发，有效期7天',
    `device_info`  varchar(200) NOT NULL DEFAULT ''                  COMMENT '设备信息（User-Agent摘要）',
    `login_ip`     varchar(45)  NOT NULL DEFAULT ''                  COMMENT '登录IP，支持IPv6',
    `status`       tinyint(2)   NOT NULL DEFAULT 1                   COMMENT '状态（0-失效、1-有效）',
    `expiry_time`  datetime     NOT NULL DEFAULT '2199-01-01 00:00:00' COMMENT '到期时间（AccessToken过期时间）',
    `deleted_at`   datetime     NULL                                 COMMENT '软删除时间，登出/强制下线时标记',
    `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    `create_by`    varchar(32)  NOT NULL DEFAULT 'sys'               COMMENT '创建人',
    `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP   COMMENT '更新时间',
    `update_by`    varchar(32)  NOT NULL DEFAULT 'sys'               COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_account_id` (`account_id`),
    KEY `idx_access_token` (`access_token`(191)),
    KEY `idx_refresh_token` (`refresh_token`(191)),
    KEY `idx_status` (`status`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '账号访问Token表';

-- ----------------------------
-- 账号角色关联表
-- ----------------------------
DROP TABLE IF EXISTS `account_role`;
CREATE TABLE `account_role`
(
    `id`          int(11)     NOT NULL AUTO_INCREMENT COMMENT '主键',
    `account_id`  int(11)     NOT NULL DEFAULT 0  COMMENT '账号ID',
    `role_code`   varchar(12) NOT NULL DEFAULT '' COMMENT '角色编码',
    `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_account_id` (`account_id`),
    KEY `idx_role_code` (`role_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '账号角色表';

-- ----------------------------
-- 账号机构关联表
-- ----------------------------
DROP TABLE IF EXISTS `account_org`;
CREATE TABLE `account_org`
(
    `id`          int(11)     NOT NULL AUTO_INCREMENT COMMENT '主键',
    `account_id`  int(11)     NOT NULL DEFAULT 0  COMMENT '账号ID',
    `org_code`    varchar(12) NOT NULL DEFAULT '' COMMENT '机构编码',
    `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_account_id` (`account_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '账号机构表';

-- ----------------------------
-- 字典表
-- ----------------------------
DROP TABLE IF EXISTS `dict_type`;
CREATE TABLE `dict_type`
(
    `id`          int(11)      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID（0=内置字典，全租户共享）',
    `dict_type`   varchar(64)  NOT NULL DEFAULT '' COMMENT '字典类型编码，蛇形命名，如sys_user_status，租户内唯一',
    `dict_name`   varchar(128) NOT NULL DEFAULT '' COMMENT '字典类型名称',
    `is_builtin`  tinyint(2)   NOT NULL DEFAULT 0  COMMENT '是否内置（0-自定义、1-系统内置，内置字典不可删除）',
    `status`      tinyint(2)   NOT NULL DEFAULT 1  COMMENT '状态（0-禁用、1-启用）',
    `remark`      varchar(256) NOT NULL DEFAULT '' COMMENT '备注',
    `deleted_at`  datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_type` (`tenant_id`, `dict_type`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '字典类型表';

-- ----------------------------
-- 字典项表
-- ----------------------------
DROP TABLE IF EXISTS `dict_item`;
CREATE TABLE `dict_item`
(
    `id`            int(11)      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID，与所属字典类型一致',
    `dict_type_id`  int(11)      NOT NULL DEFAULT 0  COMMENT '字典类型ID，关联dict_type.id',
    `dict_type`     varchar(64)  NOT NULL DEFAULT '' COMMENT '字典类型编码，冗余存储',
    `item_code`     varchar(64)  NOT NULL DEFAULT '' COMMENT '字典项编码，大写字母+下划线，同字典类型下唯一',
    `item_name`     varchar(128) NOT NULL DEFAULT '' COMMENT '字典项名称',
    `item_value`    varchar(256) NOT NULL DEFAULT '' COMMENT '字典值',
    `css_class`     varchar(64)  NOT NULL DEFAULT '' COMMENT '样式标签，如success/warning/error',
    `list_class`    varchar(64)  NOT NULL DEFAULT '' COMMENT 'Tag颜色或图标标识',
    `sort_order`    int(11)      NOT NULL DEFAULT 0  COMMENT '排序',
    `is_default`    tinyint(2)   NOT NULL DEFAULT 0  COMMENT '是否默认（0-否、1-是，同字典类型下只允许一个默认值）',
    `status`        tinyint(2)   NOT NULL DEFAULT 1  COMMENT '状态（0-禁用、1-启用）',
    `remark`        varchar(256) NOT NULL DEFAULT '' COMMENT '备注',
    `deleted_at`    datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`     varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`     varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_type_code` (`dict_type_id`, `item_code`),
    KEY `idx_dict_type` (`dict_type`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '字典项表';

-- ----------------------------
-- 系统配置表
-- ----------------------------
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config`
(
    `id`            int(11)      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID（0=内置配置全局模板，各租户可覆盖）',
    `config_key`    varchar(128) NOT NULL DEFAULT '' COMMENT '配置键，点分层级命名，如sys.site.name，租户内唯一',
    `config_name`   varchar(128) NOT NULL DEFAULT '' COMMENT '配置名称',
    `config_value`  text         NOT NULL                COMMENT '配置值，统一字符串存储',
    `config_type`   varchar(32)  NOT NULL DEFAULT 'STRING' COMMENT '值类型（STRING/INTEGER/BOOLEAN/JSON/URL）',
    `is_builtin`    tinyint(2)   NOT NULL DEFAULT 0  COMMENT '是否内置（0-自定义、1-系统内置，内置配置不可删除键）',
    `is_encrypted`  tinyint(2)   NOT NULL DEFAULT 0  COMMENT '是否加密存储（0-明文、1-加密，加密值展示脱敏）',
    `config_group`  varchar(64)  NOT NULL DEFAULT '' COMMENT '配置分组，如 基础设置/安全策略/外观设置/邮件设置/自定义',
    `remark`        varchar(256) NOT NULL DEFAULT '' COMMENT '备注，展示在编辑弹窗内',
    `deleted_at`    datetime     NULL                COMMENT '软删除时间，仅自定义配置可软删除，内置配置永远为NULL',
    `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`     varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`     varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_key` (`tenant_id`, `config_key`),
    KEY `idx_tenant_group` (`tenant_id`, `config_group`),
    KEY `idx_is_builtin` (`is_builtin`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统配置表';

-- ----------------------------
-- 系统日志表（操作日志 + 登录日志）
-- type: 1-登录 2-操作 9-其他
-- ----------------------------
DROP TABLE IF EXISTS `sys_log`;
CREATE TABLE `sys_log`
(
    `id`            bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `belong_code`   varchar(16)  NOT NULL DEFAULT '' COMMENT '所属对象编码',
    `belong_name`   varchar(64)  NOT NULL DEFAULT '' COMMENT '所属对象名称',
    `type`          tinyint(2)   NOT NULL DEFAULT 0  COMMENT '日志类型（1-登录、2-操作、9-其他）',
    `operator_code` varchar(64)  NOT NULL DEFAULT '' COMMENT '操作人编码',
    `operator_name` varchar(128) NOT NULL DEFAULT '' COMMENT '操作人姓名',
    `operate_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    `operation`     varchar(128) NOT NULL DEFAULT '' COMMENT '操作名称',
    `content`       text                  COMMENT '操作内容（JSON，含before/after/params）',
    `result`        tinyint(2)   NOT NULL DEFAULT 1  COMMENT '操作结果（0-失败、1-成功）',
    `menu`          varchar(128) NOT NULL DEFAULT '' COMMENT '操作模块',
    `ip`            varchar(64)  NOT NULL DEFAULT '' COMMENT '客户端IP地址',
    `deleted_at`    datetime     NULL                COMMENT '软删除时间，NULL表示有效记录',
    `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`     varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`     varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_type` (`type`),
    KEY `idx_operate_time` (`operate_time`),
    KEY `idx_menu` (`menu`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统日志表';

-- ----------------------------
-- 中国行政区编码表
-- ----------------------------
DROP TABLE IF EXISTS `chinese_area_code`;
CREATE TABLE `chinese_area_code`
(
    `id`          bigint(20)  NOT NULL DEFAULT 0  COMMENT '行政区编码',
    `pid`         bigint(20)  NOT NULL DEFAULT 0  COMMENT '父编码',
    `name`        varchar(64) NOT NULL DEFAULT '' COMMENT '名称',
    `type`        tinyint(4)  NOT NULL DEFAULT 1  COMMENT '类型（1-省级、2-市级、3-区县、4-乡镇、5-街道/村）',
    `status`      tinyint(4)  NOT NULL DEFAULT 1  COMMENT '状态（0-禁用、1-启用）',
    `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_pid` (`pid`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '中国行政区编码表';
