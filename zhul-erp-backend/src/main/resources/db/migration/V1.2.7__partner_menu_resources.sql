-- V1.2.7：客商管理（客户管理 + 供应商管理）的菜单资源与按钮权限。
-- sort=4，排在商品管理（3）之后、系统管理（98）之前。
INSERT INTO `resource` (`id`, `pid`, `code`, `name`, `type`, `sort`, `light_icon`, `light_selected_icon`, `dark_icon`, `dark_selected_icon`, `path`, `status`, `micro_app`, `create_by`, `update_by`) VALUES
(100006, 0, 'RS1100006', '客商管理', 1, 4, 'contacts', '', '', '', '/partner', 1, '', 'sys', 'sys');

INSERT INTO `resource` (`id`, `pid`, `code`, `name`, `type`, `sort`, `light_icon`, `light_selected_icon`, `dark_icon`, `dark_selected_icon`, `path`, `status`, `micro_app`, `create_by`, `update_by`) VALUES
(100061, 100006, 'RS2100061', '客户管理',   2, 1, 'solution', '', '', '', '/partner/customers', 1, '', 'sys', 'sys'),
(100062, 100006, 'RS2100062', '供应商管理', 2, 2, 'shop',     '', '', '', '/partner/suppliers',  1, '', 'sys', 'sys');

-- 客户管理按钮（pid=100061）
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`) VALUES
(110141, 100061, 'RS3110141', '新增客户', 3, 1, '','','','', '', 'partner:customer:add',    1,'','sys','sys'),
(110142, 100061, 'RS3110142', '编辑客户', 3, 2, '','','','', '', 'partner:customer:edit',   1,'','sys','sys'),
(110143, 100061, 'RS3110143', '删除客户', 3, 3, '','','','', '', 'partner:customer:delete', 1,'','sys','sys'),
(110144, 100061, 'RS3110144', '启用禁用', 3, 4, '','','','', '', 'partner:customer:status', 1,'','sys','sys');

-- 供应商管理按钮（pid=100062）
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`) VALUES
(110151, 100062, 'RS3110151', '新增供应商', 3, 1, '','','','', '', 'partner:supplier:add',    1,'','sys','sys'),
(110152, 100062, 'RS3110152', '编辑供应商', 3, 2, '','','','', '', 'partner:supplier:edit',   1,'','sys','sys'),
(110153, 100062, 'RS3110153', '删除供应商', 3, 3, '','','','', '', 'partner:supplier:delete', 1,'','sys','sys'),
(110154, 100062, 'RS3110154', '启用禁用',   3, 4, '','','','', '', 'partner:supplier:status', 1,'','sys','sys');

-- 客商管理菜单树（1个目录+2个菜单+8个按钮=11个资源id）加进标准版、旗舰版两个套餐，
-- 客户/供应商是基础主数据，不做套餐分层（跟询盘管理不同，那个是旗舰版专属）。
UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(
    JSON_ARRAY_APPEND(
        JSON_ARRAY_APPEND(
            JSON_ARRAY_APPEND(
                JSON_ARRAY_APPEND(
                    JSON_ARRAY_APPEND(
                        JSON_ARRAY_APPEND(
                            JSON_ARRAY_APPEND(
                                JSON_ARRAY_APPEND(
                                    JSON_ARRAY_APPEND(
                                        JSON_ARRAY_APPEND(`menu_ids`, '$', 100006),
                                        '$', 100061),
                                    '$', 100062),
                                '$', 110141),
                            '$', 110142),
                        '$', 110143),
                    '$', 110144),
                '$', 110151),
            '$', 110152),
        '$', 110153),
    '$', 110154)
WHERE `name` IN ('标准版', '旗舰版');
