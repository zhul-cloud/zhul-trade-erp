-- 菜单命名统一字数迁移 V1.3.6 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.3.6 的记录）。

UPDATE `resource` r
JOIN (
              SELECT 100116 AS id, '素材库' AS name
    UNION ALL SELECT 100053, '商机'
    UNION ALL SELECT 100061, '客户'
    UNION ALL SELECT 100051, '询盘单'
    UNION ALL SELECT 100073, '报价单'
    UNION ALL SELECT 100078, 'PI'
    UNION ALL SELECT 100089, '供应商发货'
    UNION ALL SELECT 100062, '供应商'
    UNION ALL SELECT 100097, '出运单'
    UNION ALL SELECT 100085, '收款单'
    UNION ALL SELECT 100110, '退款单'
    UNION ALL SELECT 100107, '结汇单'
    UNION ALL SELECT 100103, '应付单'
    UNION ALL SELECT 100104, '付款单'
    UNION ALL SELECT 100105, '报销单'
    UNION ALL SELECT 100106, '费用单'
    UNION ALL SELECT 100034, '商品'
    UNION ALL SELECT 100031, '品牌'
    UNION ALL SELECT 100032, '品类'
    UNION ALL SELECT 100033, '系列'
    UNION ALL SELECT 100075, '汇率'
    UNION ALL SELECT 100011, '用户'
    UNION ALL SELECT 100012, '角色'
    UNION ALL SELECT 100014, '部门'
    UNION ALL SELECT 100015, '岗位'
    UNION ALL SELECT 100013, '菜单'
    UNION ALL SELECT 100016, '字典'
    UNION ALL SELECT 100021, '租户'
    UNION ALL SELECT 100022, '套餐'
) n ON n.id = r.id
SET r.`name` = n.name, r.`update_time` = NOW(), r.`update_by` = 'sys';

-- 物流管理恢复为 4，迁移时后移的一级菜单各减 1
UPDATE `resource` SET `sort` = 4, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100096;
UPDATE `resource` SET `sort` = `sort` - 1, `update_time` = NOW(), `update_by` = 'sys'
WHERE `pid` = 0 AND `sort` >= 6 AND `id` NOT IN (100002, 100003, 100096);
