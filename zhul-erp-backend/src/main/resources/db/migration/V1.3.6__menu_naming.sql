-- 菜单命名统一字数（openspec menu-naming-four-chars）：
-- 1. 二级菜单统一 4 个字，按菜单编号改名（页面地址、权限码不变）；「工作台」保留 3 个字；
-- 2. 一级菜单排序去重：仓库管理与物流管理都是 4，排序号 ≥ 5 的一级菜单（系统管理、租户管理除外）各加 1，物流管理设为 5，
--    其余一级菜单的相对顺序不变（保留各环境在菜单管理里手工调过的顺序）。

UPDATE `resource` r
JOIN (
              SELECT 100116 AS id, '素材管理' AS name
    UNION ALL SELECT 100053, '商机管理'
    UNION ALL SELECT 100061, '客户管理'
    UNION ALL SELECT 100051, '询盘管理'
    UNION ALL SELECT 100073, '报价管理'
    UNION ALL SELECT 100078, '形式发票'
    UNION ALL SELECT 100089, '发货跟踪'
    UNION ALL SELECT 100062, '供应商库'
    UNION ALL SELECT 100097, '出运管理'
    UNION ALL SELECT 100085, '收款管理'
    UNION ALL SELECT 100110, '退款管理'
    UNION ALL SELECT 100107, '结汇管理'
    UNION ALL SELECT 100103, '应付管理'
    UNION ALL SELECT 100104, '付款管理'
    UNION ALL SELECT 100105, '报销管理'
    UNION ALL SELECT 100106, '费用管理'
    UNION ALL SELECT 100034, '商品档案'
    UNION ALL SELECT 100031, '品牌管理'
    UNION ALL SELECT 100032, '品类管理'
    UNION ALL SELECT 100033, '系列管理'
    UNION ALL SELECT 100075, '汇率设置'
    UNION ALL SELECT 100011, '用户管理'
    UNION ALL SELECT 100012, '角色管理'
    UNION ALL SELECT 100014, '部门管理'
    UNION ALL SELECT 100015, '岗位管理'
    UNION ALL SELECT 100013, '菜单管理'
    UNION ALL SELECT 100016, '字典管理'
    UNION ALL SELECT 100021, '租户列表'
    UNION ALL SELECT 100022, '套餐管理'
) n ON n.id = r.id
SET r.`name` = n.name, r.`update_time` = NOW(), r.`update_by` = 'sys';

UPDATE `resource` SET `sort` = `sort` + 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `pid` = 0 AND `sort` >= 5 AND `id` NOT IN (100002, 100003, 100096);
UPDATE `resource` SET `sort` = 5, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100096;
