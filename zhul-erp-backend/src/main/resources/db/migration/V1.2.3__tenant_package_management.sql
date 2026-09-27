-- 租户套餐管理：套餐名称/描述字段按 PRD 校验规则（2~100 / 0~500 字符）放宽列宽，
-- 并补充软删除标记（NEVER 规则：不物理删除业务数据）。
ALTER TABLE `tenant_package`
    MODIFY COLUMN `name`   varchar(100) NOT NULL DEFAULT '' COMMENT '套餐名称',
    MODIFY COLUMN `remark` varchar(500) NOT NULL DEFAULT '' COMMENT '套餐描述',
    ADD COLUMN `deleted_at` datetime NULL COMMENT '软删除时间，NULL表示未删除' AFTER `status`,
    ADD INDEX `idx_deleted_at` (`deleted_at`);

-- 套餐管理按钮权限（pid=100022，对应 V1.0.1 中已建好的「套餐管理」菜单）
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`) VALUES
(110091, 100022, 'RS3110091', '新增套餐', 3, 1, '','','','', '', 'tenant:package:add',    1,'','sys','sys'),
(110092, 100022, 'RS3110092', '编辑套餐', 3, 2, '','','','', '', 'tenant:package:edit',   1,'','sys','sys'),
(110093, 100022, 'RS3110093', '禁用启用', 3, 3, '','','','', '', 'tenant:package:status', 1,'','sys','sys'),
(110094, 100022, 'RS3110094', '删除套餐', 3, 4, '','','','', '', 'tenant:package:delete', 1,'','sys','sys');
