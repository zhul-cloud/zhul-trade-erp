-- 11-16 反馈第二批（openspec feedback-1116-batch2）
-- 1. 货代独立管理：货代仍是供应商表里类型为服务商（supplier_type = 3）的记录，新增归属客户（客户指定货代）
-- 2. 公海客户：客户放入公海、认领
-- 3. 菜单「物流管理 → 货代管理」及按钮

ALTER TABLE `supplier`
    ADD COLUMN `customer_id` bigint NULL COMMENT '归属客户ID（只对服务商有意义）：NULL 为公司货代，有值为该客户的指定货代，关联customer.id' AFTER `volume_divisor`,
    ADD KEY `idx_supplier_customer_id` (`customer_id`);

ALTER TABLE `customer`
    ADD COLUMN `in_pool` tinyint(2) NOT NULL DEFAULT 0 COMMENT '是否在公海（0-否、1-是）' AFTER `owner_id`,
    ADD COLUMN `pool_at` datetime NULL COMMENT '放入公海时间' AFTER `in_pool`,
    ADD COLUMN `pool_by` bigint NOT NULL DEFAULT 0 COMMENT '放入公海的人，user_basic.id，0 为无' AFTER `pool_at`,
    ADD COLUMN `pool_reason` varchar(200) NOT NULL DEFAULT '' COMMENT '放入公海原因' AFTER `pool_by`,
    ADD COLUMN `last_owner_id` bigint NOT NULL DEFAULT 0 COMMENT '放入公海前的负责业务员，user_basic.id，0 为无' AFTER `pool_reason`,
    ADD KEY `idx_customer_tenant_pool` (`tenant_id`, `in_pool`);

-- 菜单与按钮：货代管理排在运费账单之后
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
              SELECT 110243 AS id, 100096 AS pid, 'RS2110243' AS code, '货代管理' AS name, 2 AS type, 3 AS sort, 'car' AS li, '' AS lsi, '' AS di, '' AS dsi,
                     '/logistics/forwarders' AS path, '' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
    UNION ALL SELECT 110244, 110243, 'RS3110244', '新增货代', 3, 1, '', '', '', '', '', 'logistics:forwarder:add', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110245, 110243, 'RS3110245', '编辑货代', 3, 2, '', '', '', '', '', 'logistics:forwarder:edit', 1, '', 'sys', 'sys'
    UNION ALL SELECT 110246, 110243, 'RS3110246', '启用/停用货代', 3, 3, '', '', '', '', '', 'logistics:forwarder:status', 1, '', 'sys', 'sys'
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.`id`);

-- 加进含「出运单」菜单的套餐（套餐只存页面与按钮，不存分组）
UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(`menu_ids`,
        '$', 110243), '$', 110244), '$', 110245), '$', 110246)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`) AND JSON_CONTAINS(`menu_ids`, '100097') AND NOT JSON_CONTAINS(`menu_ids`, '110243');

-- 授给已有「出运单」菜单的角色（含内置租户管理员）：原来在供应商里维护货代的人
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT rr.`role_code`, v.`id`, v.`code`
FROM `role_resource` rr
JOIN (SELECT 110243 AS id, 'RS2110243' AS code UNION ALL SELECT 110244, 'RS3110244'
      UNION ALL SELECT 110245, 'RS3110245' UNION ALL SELECT 110246, 'RS3110246') v
WHERE rr.`resource_id` = 100097
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = rr.`role_code` AND y.`resource_id` = v.`id`);

-- 回退（先确认没有公海客户：SELECT COUNT(*) FROM customer WHERE in_pool = 1，有的话这些客户会变成未分配）：
-- DELETE FROM `role_resource` WHERE `resource_id` IN (110243, 110244, 110245, 110246);
-- UPDATE `tenant_package` SET `menu_ids` = COALESCE((SELECT JSON_ARRAYAGG(v) FROM JSON_TABLE(`menu_ids`, '$[*]' COLUMNS(v INT PATH '$')) t
--     WHERE v NOT IN (110243, 110244, 110245, 110246)), JSON_ARRAY())
-- WHERE JSON_VALID(`menu_ids`) AND JSON_CONTAINS(`menu_ids`, '110243');
-- DELETE FROM `resource` WHERE `id` IN (110243, 110244, 110245, 110246);
-- ALTER TABLE `customer` DROP KEY `idx_customer_tenant_pool`, DROP COLUMN `last_owner_id`, DROP COLUMN `pool_reason`,
--     DROP COLUMN `pool_by`, DROP COLUMN `pool_at`, DROP COLUMN `in_pool`;
-- ALTER TABLE `supplier` DROP KEY `idx_supplier_customer_id`, DROP COLUMN `customer_id`;
