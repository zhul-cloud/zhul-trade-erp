-- 采购需求删除（openspec purchase-requirement-delete）：删除还没下单的数量，记为来源型号行的「不需采购」
ALTER TABLE `purchase_requirement`
    ADD COLUMN `removed_qty`   int(11)      NOT NULL DEFAULT 0 COMMENT '删除的数量（不需采购，0 表示没有删除）' AFTER `quantity`,
    ADD COLUMN `remove_reason` varchar(200) NULL               COMMENT '删除原因，如「仓库有库存：仓库有 100 个现货」' AFTER `removed_qty`;

-- 按钮「删除采购需求」，挂在「采购需求」菜单下
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
    SELECT 110247 AS id, 100087 AS pid, 'RS3110247' AS code, '删除采购需求' AS name, 3 AS type, 3 AS sort, '' AS li, '' AS lsi, '' AS di, '' AS dsi,
           '' AS path, 'purchase:requirement:delete' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.`id`);

-- 加进含「拆分采购需求」的套餐
UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 110247)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`) AND JSON_CONTAINS(`menu_ids`, '110187') AND NOT JSON_CONTAINS(`menu_ids`, '110247');

-- 授给已有「拆分采购需求」按钮的角色
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT rr.`role_code`, 110247, 'RS3110247'
FROM `role_resource` rr
WHERE rr.`resource_id` = 110187
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = rr.`role_code` AND y.`resource_id` = 110247);
