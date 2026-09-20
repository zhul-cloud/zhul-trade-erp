-- data_v1.2.sql：v1.2.0 商品主数据的菜单与按钮权限种子（依赖 data_v1.sql，可重复执行）
-- 编码规则沿用 data_v1.sql：code = 'RS' + type + id
-- 权限码见 openspec/changes/archive/2026-09-20-add-product-master-core/design.md 决策 2；写接口另需平台账号（tenantId=0），
-- 所以租户管理员即使被分配了这些按钮，也只能读、不能写。
USE zhul_erp;

DELETE FROM `resource` WHERE id = 100004 OR id BETWEEN 100031 AND 100034 OR id BETWEEN 110101 AND 110133;

-- 顶级菜单
INSERT INTO `resource` (`id`, `pid`, `code`, `name`, `type`, `sort`, `light_icon`, `light_selected_icon`, `dark_icon`, `dark_selected_icon`, `path`, `status`, `micro_app`, `create_by`, `update_by`) VALUES
(100004, 0, 'RS1100004', '商品管理', 1, 3, 'shopping', '', '', '', '/product', 1, '', 'sys', 'sys');

-- 商品管理子菜单
INSERT INTO `resource` (`id`, `pid`, `code`, `name`, `type`, `sort`, `light_icon`, `light_selected_icon`, `dark_icon`, `dark_selected_icon`, `path`, `status`, `micro_app`, `create_by`, `update_by`) VALUES
(100031, 100004, 'RS2100031', '品牌管理', 2, 1, 'tag',      '', '', '', '/product/brands',     1, '', 'sys', 'sys'),
(100032, 100004, 'RS2100032', '品类管理', 2, 2, 'appstore', '', '', '', '/product/categories', 1, '', 'sys', 'sys'),
(100033, 100004, 'RS2100033', '系列管理', 2, 3, 'cluster',  '', '', '', '/product/series',     1, '', 'sys', 'sys'),
(100034, 100004, 'RS2100034', '商品列表', 2, 4, 'database', '', '', '', '/product/products',   1, '', 'sys', 'sys');

-- 按钮权限（type=3）：品牌（pid=100031）
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`) VALUES
(110101, 100031, 'RS3110101', '新增品牌', 3, 1, '','','','', '', 'product:brand:add',    1,'','sys','sys'),
(110102, 100031, 'RS3110102', '编辑品牌', 3, 2, '','','','', '', 'product:brand:edit',   1,'','sys','sys'),
(110103, 100031, 'RS3110103', '删除品牌', 3, 3, '','','','', '', 'product:brand:delete', 1,'','sys','sys');

-- 品类（pid=100032）
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`) VALUES
(110111, 100032, 'RS3110111', '新增品类', 3, 1, '','','','', '', 'product:category:add',    1,'','sys','sys'),
(110112, 100032, 'RS3110112', '编辑品类', 3, 2, '','','','', '', 'product:category:edit',   1,'','sys','sys'),
(110113, 100032, 'RS3110113', '删除品类', 3, 3, '','','','', '', 'product:category:delete', 1,'','sys','sys');

-- 系列（pid=100033）
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`) VALUES
(110121, 100033, 'RS3110121', '新增系列', 3, 1, '','','','', '', 'product:series:add',    1,'','sys','sys'),
(110122, 100033, 'RS3110122', '编辑系列', 3, 2, '','','','', '', 'product:series:edit',   1,'','sys','sys'),
(110123, 100033, 'RS3110123', '删除系列', 3, 3, '','','','', '', 'product:series:delete', 1,'','sys','sys');

-- 商品（pid=100034）：编辑覆盖规格、型号关系、技术资料、应用场景、FAQ（含审核）、图片视频、物流、海关、参考价
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`) VALUES
(110131, 100034, 'RS3110131', '新增商品', 3, 1, '','','','', '', 'product:product:add',    1,'','sys','sys'),
(110132, 100034, 'RS3110132', '编辑商品', 3, 2, '','','','', '', 'product:product:edit',   1,'','sys','sys'),
(110133, 100034, 'RS3110133', '删除商品', 3, 3, '','','','', '', 'product:product:delete', 1,'','sys','sys');
