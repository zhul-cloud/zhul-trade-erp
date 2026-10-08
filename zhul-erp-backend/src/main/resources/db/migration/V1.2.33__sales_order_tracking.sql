-- ===========================
-- 销售订单扩充：销售日期、新 / 老客户、现货 / 期货、按型号的进度与采购员、手动创建订单与订单上的收款
-- 见 openspec/changes/expand-sales-order
-- ===========================

ALTER TABLE `sales_order`
    MODIFY COLUMN `pi_id` bigint NOT NULL DEFAULT 0 COMMENT '来源 PI ID，关联proforma_invoice.id；手动创建的订单为 0',
    ADD COLUMN `source`          tinyint(2)    NOT NULL DEFAULT 1 COMMENT '来源（1-PI 转成、2-手动创建）' AFTER `so_no`,
    ADD COLUMN `sales_date`      date          NULL COMMENT '销售日期（统计口径）' AFTER `source`,
    ADD COLUMN `customer_type`   tinyint(2)    NOT NULL DEFAULT 1 COMMENT '新老客户快照（1-新客户、2-老客户）' AFTER `customer_id`,
    ADD COLUMN `stock_type`      tinyint(2)    NOT NULL DEFAULT 1 COMMENT '现货 / 期货（1-现货、2-期货），任一型号为期货即为期货' AFTER `customer_type`,
    ADD COLUMN `progress_code`   varchar(32)   NOT NULL DEFAULT 'PENDING_PURCHASE' COMMENT '订单进度（字典 sales_order_status 的 item_code），取型号中最靠前的进度；客户收货后为 COMPLETED' AFTER `status`,
    ADD COLUMN `completed_at`    datetime      NULL COMMENT '客户确认收货时间' AFTER `progress_code`,
    ADD COLUMN `receipt_status`  tinyint(2)    NOT NULL DEFAULT 1 COMMENT '收款状态（1-未付款、2-待到账、3-部分到账、4-已到账），仅手动创建的订单；PI 转成的订单取 PI' AFTER `completed_at`,
    ADD COLUMN `received_amount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '已到账（原币，毛额），仅手动创建的订单' AFTER `receipt_status`,
    ADD COLUMN `fee_diff_amount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '银行中转手续费差额（原币），仅手动创建的订单' AFTER `received_amount`,
    ADD KEY `idx_so_sales_date` (`sales_date`),
    ADD KEY `idx_so_progress` (`progress_code`),
    ADD KEY `idx_so_stock_type` (`stock_type`),
    ADD KEY `idx_so_customer_type` (`customer_type`),
    ADD KEY `idx_so_source` (`source`);

ALTER TABLE `sales_order_item`
    ADD COLUMN `stock_type`    tinyint(2)  NOT NULL DEFAULT 1 COMMENT '现货 / 期货（1-现货、2-期货）' AFTER `lead_time`,
    ADD COLUMN `progress_code` varchar(32) NOT NULL DEFAULT 'PENDING_PURCHASE' COMMENT '型号进度（字典 sales_order_status 的 item_code）' AFTER `stock_type`,
    ADD COLUMN `purchaser_id`  bigint      NULL COMMENT '采购员用户ID，关联user_basic.id；为空表示未指定' AFTER `progress_code`,
    ADD KEY `idx_so_item_purchaser` (`purchaser_id`);

ALTER TABLE `payment_receipt`
    ADD COLUMN `so_id` bigint NULL COMMENT '手动创建的订单ID，关联sales_order.id；PI 的收款为空。PI 与订单都为空表示未认领到账' AFTER `pi_id`,
    ADD KEY `idx_receipt_so_id` (`so_id`);

-- 存量回填：来源 PI；现货 / 期货按货期（1-现货）；采购员取成本价对应回价的询价人；新老客户取来源询盘；销售日期取最早的有效水单 / 到账日期
UPDATE `sales_order_item` SET `stock_type` = IF(`lead_time` = 1, 1, 2);

UPDATE `sales_order_item` i
JOIN `quotation_item` q ON q.`id` = i.`quotation_item_id`
JOIN `sourcing_quote` s ON s.`id` = q.`cost_quote_id`
SET i.`purchaser_id` = s.`quoted_by`
WHERE s.`quoted_by` > 0;

UPDATE `sales_order` o
SET o.`stock_type` = IF(EXISTS (SELECT 1 FROM `sales_order_item` i WHERE i.`so_id` = o.`id` AND i.`stock_type` = 2 AND i.`deleted_at` IS NULL), 2, 1),
    o.`customer_type` = IF(EXISTS (SELECT 1 FROM `sales_order_item` i JOIN `customer_inquiry` c ON c.`id` = i.`customer_inquiry_id`
                                   WHERE i.`so_id` = o.`id` AND c.`customer_type` = 2), 2, 1),
    o.`sales_date` = COALESCE((SELECT MIN(r.`receipt_date`) FROM `payment_receipt` r
                               WHERE r.`pi_id` = o.`pi_id` AND r.`status` = 1 AND r.`deleted_at` IS NULL), DATE(o.`create_time`));

-- 平台字典「销售订单状态」：PENDING_PURCHASE、COMPLETED、CANCELLED 为内置码（代码依赖，不受启停影响），其余步骤按排序推进
INSERT INTO `dict_type` (`tenant_id`, `dict_type`, `dict_name`, `is_builtin`, `status`, `remark`, `create_by`, `update_by`)
SELECT 0, 'sales_order_status', '销售订单状态', 1, 1, '销售订单按型号的进度，按排序依次推进；待采购（PENDING_PURCHASE）、已完成（COMPLETED）、已取消（CANCELLED）为内置，请勿改编码或停用；中间步骤可改名、调整顺序、停用', 'sys', 'sys'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `dict_type` WHERE `dict_type` = 'sales_order_status' AND `tenant_id` = 0);

INSERT INTO `dict_item` (`tenant_id`, `dict_type_id`, `dict_type`, `item_code`, `item_name`, `item_name_en`, `item_value`, `sort_order`, `is_default`, `status`, `remark`, `create_by`, `update_by`)
SELECT 0, t.`id`, t.`dict_type`, v.code, v.name, v.en, '', v.s, v.d, 1, v.remark, 'sys', 'sys'
FROM `dict_type` t
JOIN (
              SELECT 'PENDING_PURCHASE' AS code, '待采购' AS name, 'Pending Purchase' AS en, 1 AS s, 1 AS d, '内置：订单创建后的第一步' AS remark
    UNION ALL SELECT 'ORDERED',      '已下单',   'Ordered',         2, 0, '已向供应商下单'
    UNION ALL SELECT 'RECEIVED',     '已入库',   'Received',        3, 0, '供应商发到我们仓库并验完货'
    UNION ALL SELECT 'TO_FORWARDER', '已交货代', 'To Forwarder',    4, 0, '已发给货代'
    UNION ALL SELECT 'SHIPPED',      '已出运',   'Shipped',         5, 0, '货代已发出'
    UNION ALL SELECT 'COMPLETED',    '已完成',   'Completed',       90, 0, '内置：客户确认收货'
    UNION ALL SELECT 'CANCELLED',    '已取消',   'Cancelled',       99, 0, '内置：订单取消'
) v
WHERE t.`dict_type` = 'sales_order_status' AND t.`tenant_id` = 0
  AND NOT EXISTS (SELECT 1 FROM `dict_item` i WHERE i.`dict_type_id` = t.`id` AND i.`item_code` = v.code);

-- 销售订单下的按钮权限：手动创建订单、更新订单进度
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
              SELECT 110185 AS id, 100079 AS pid, 'RS3110185' AS code, '手动创建订单' AS name, 3 AS type, 1 AS sort, '' AS li, '' AS lsi, '' AS di, '' AS dsi,
                     '' AS path, 'sales:order:create' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
    UNION ALL SELECT 110186, 100079, 'RS3110186', '更新订单进度', 3, 2, '', '', '', '', '', 'sales:order:progress', 1, '', 'sys', 'sys'
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.id);

UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(`menu_ids`, '$', 110185), '$', 110186)
WHERE `name` IN ('标准版', '旗舰版') AND NOT JSON_CONTAINS(`menu_ids`, '110185');

-- 授给已有「销售订单」菜单的角色
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, s.`id`, s.`code`
FROM `role_resource` x
JOIN `resource` s ON s.`id` IN (110185, 110186)
WHERE x.`resource_id` = 100079
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = s.`id`);
