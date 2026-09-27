-- V1.2.6：询盘中心的菜单资源（结构性数据）。
-- 询盘模块上线时前端 config/routes.ts 直接加了路由，但一直没有往 resource 表补记录，
-- 导致「菜单管理」页面看不到它、套餐/角色的菜单树也选不到它。这里补上，sort 排在商品管理之后、
-- 系统管理之前，跟 config/routes.ts 里业务菜单的实际先后顺序保持一致。
INSERT INTO `resource` (`id`, `pid`, `code`, `name`, `type`, `sort`, `light_icon`, `light_selected_icon`, `dark_icon`, `dark_selected_icon`, `path`, `status`, `micro_app`, `create_by`, `update_by`) VALUES
(100005, 0, 'RS1100005', '询盘管理', 1, 3, 'mail', '', '', '', '/inquiry', 1, '', 'sys', 'sys');

INSERT INTO `resource` (`id`, `pid`, `code`, `name`, `type`, `sort`, `light_icon`, `light_selected_icon`, `dark_icon`, `dark_selected_icon`, `path`, `status`, `micro_app`, `create_by`, `update_by`) VALUES
(100051, 100005, 'RS2100051', '询盘单列表', 2, 1, 'inbox',        '', '', '', '/inquiry/customer-inquiries', 1, '', 'sys', 'sys'),
(100052, 100005, 'RS2100052', '订单管理',   2, 2, 'shoppingCart', '', '', '', '/inquiry/orders',             1, '', 'sys', 'sys');
