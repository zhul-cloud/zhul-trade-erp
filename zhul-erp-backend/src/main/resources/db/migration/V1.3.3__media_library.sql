-- 素材库（openspec media-library）：
-- 1. 素材索引表 media_item：一条素材 × 一个型号一行，指向来源文件记录，记录「可发客户」「用于发品」两个标记；
-- 2. 附件的型号标注表 biz_attachment_model：供方发货、验收入库（含不良品）、打包发货的图片与视频标出涉及的型号；
-- 3. 菜单「运营管理 → 素材库」与两个按钮权限；
-- 4. 转换历史数据：实拍任务改为营销拍摄（实物图归营销实拍、拆箱验货视频归验收入库的验货记录、待拍摄任务去重），
--    已有的发货、验收、回价、客户询盘附件写入素材索引。
-- 型号归并键 asset_key = 小写(去空格的品牌 + ' ' + 去空格的型号)，与 ReceivingSupport.assetKey 一致。

CREATE TABLE `media_item` (
    `id`          bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)      NOT NULL DEFAULT 0 COMMENT '租户ID',
    `asset_key`   varchar(255) NOT NULL DEFAULT '' COMMENT '型号归并键：小写（品牌 + 空格 + 型号），去首尾空格',
    `brand`       varchar(100) NOT NULL DEFAULT '' COMMENT '品牌（显示用）',
    `model`       varchar(150) NOT NULL DEFAULT '' COMMENT '型号（显示用）',
    `category`    tinyint(2)   NOT NULL DEFAULT 0 COMMENT '类别（1-官方图片、2-产品文档、3-客户来图、4-供方实拍、5-网络图片、6-供方发货、7-验货记录、8-到货异常、9-营销实拍、10-打包发货、11-客户反馈）',
    `kind`        tinyint(2)   NOT NULL DEFAULT 1 COMMENT '文件种类（1-图片、2-视频、3-文件）',
    `source_table` varchar(64) NOT NULL DEFAULT '' COMMENT '来源文件记录所在表（biz_attachment、customer_inquiry_attachment、product_media、product_document）',
    `source_id`   bigint(20)   NOT NULL DEFAULT 0 COMMENT '来源文件记录ID',
    `can_send`    tinyint(2)   NOT NULL DEFAULT 0 COMMENT '是否可发客户（0-仅内部、1-可发客户）',
    `for_listing` tinyint(2)   NOT NULL DEFAULT 0 COMMENT '是否用于发品（0-否、1-是）',
    `note`        varchar(500) NOT NULL DEFAULT '' COMMENT '说明（网络图片的来源网址等）',
    `flag_by`     bigint(20)   NOT NULL DEFAULT 0 COMMENT '最后修改标记的用户ID，0 表示默认值',
    `flag_time`   datetime     NULL COMMENT '最后修改标记的时间',
    `deleted_at`  datetime     NULL COMMENT '软删除时间，NULL表示未删除（来源被删除或作废时移出素材库）',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_source_asset` (`tenant_id`, `source_table`, `source_id`, `asset_key`),
    KEY `idx_tenant_asset` (`tenant_id`, `asset_key`),
    KEY `idx_tenant_category` (`tenant_id`, `category`),
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='素材索引：按型号汇总各环节的图片、视频与资料';

CREATE TABLE `biz_attachment_model` (
    `id`            bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     int(11)      NOT NULL DEFAULT 0 COMMENT '租户ID',
    `attachment_id` bigint(20)   NOT NULL DEFAULT 0 COMMENT '业务附件ID，关联biz_attachment.id',
    `asset_key`     varchar(255) NOT NULL DEFAULT '' COMMENT '型号归并键',
    `brand`         varchar(100) NOT NULL DEFAULT '' COMMENT '品牌',
    `model`         varchar(150) NOT NULL DEFAULT '' COMMENT '型号',
    `deleted_at`    datetime     NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`     varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`     varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_attachment_asset` (`attachment_id`, `asset_key`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务附件的型号标注';

-- ---------------------------------------------------------------- 菜单与权限

INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT 100116, 100101, 'RS2100116', '素材库', 2, 4, 'picture', '', '', '', '/operations/media', '', 1, '', 'sys', 'sys'
WHERE NOT EXISTS (SELECT 1 FROM `resource` WHERE `id` = 100116);

INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT 110211, 100116, 'RS3110211', '上传网络图片', 3, 1, '', '', '', '', '', 'media:library:upload', 1, '', 'sys', 'sys'
WHERE NOT EXISTS (SELECT 1 FROM `resource` WHERE `id` = 110211);

INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT 110212, 100116, 'RS3110212', '修改素材标记', 3, 2, '', '', '', '', '', 'media:library:edit', 1, '', 'sys', 'sys'
WHERE NOT EXISTS (SELECT 1 FROM `resource` WHERE `id` = 110212);

-- 套餐：所有套餐都加素材库与两个按钮，以及运营管理分组
UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 100116)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`) AND NOT JSON_CONTAINS(`menu_ids`, '100116');
UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 110211)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`) AND NOT JSON_CONTAINS(`menu_ids`, '110211');
UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 110212)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`) AND NOT JSON_CONTAINS(`menu_ids`, '110212');
UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 100101)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`) AND NOT JSON_CONTAINS(`menu_ids`, '100101');

-- 租户管理员：全部；仓库、拍摄：查看（含运营管理分组）
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` IN (100101, 100116, 110211, 110212)
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` IN (100101, 100116)
WHERE r.`code` IN ('ROLE_WH', 'ROLE_SHOOTER') AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);

-- ---------------------------------------------------------------- 历史实拍数据

-- 实拍任务里的实物图 → 营销实拍（可发客户）
INSERT INTO `media_item` (`tenant_id`, `asset_key`, `brand`, `model`, `category`, `kind`, `source_table`, `source_id`, `can_send`)
SELECT a.`tenant_id`, LOWER(CONCAT(TRIM(t.`brand`), ' ', TRIM(t.`model`))), TRIM(t.`brand`), TRIM(t.`model`), 9, 1, 'biz_attachment', a.`id`, 1
FROM `biz_attachment` a
JOIN `shoot_task` t ON t.`id` = a.`owner_id`
WHERE a.`owner_type` = 'SHOOT' AND a.`kind` = 1 AND a.`deleted_at` IS NULL AND t.`deleted_at` IS NULL
ON DUPLICATE KEY UPDATE `category` = VALUES(`category`);

-- 拆箱、验货视频 → 来源采购入库单的验货记录，并标注该型号
INSERT INTO `biz_attachment_model` (`tenant_id`, `attachment_id`, `asset_key`, `brand`, `model`)
SELECT a.`tenant_id`, a.`id`, LOWER(CONCAT(TRIM(t.`brand`), ' ', TRIM(t.`model`))), TRIM(t.`brand`), TRIM(t.`model`)
FROM `biz_attachment` a
JOIN `shoot_task` t ON t.`id` = a.`owner_id`
WHERE a.`owner_type` = 'SHOOT' AND a.`kind` = 2 AND a.`deleted_at` IS NULL
ON DUPLICATE KEY UPDATE `deleted_at` = NULL;

UPDATE `biz_attachment` a
JOIN `shoot_task` t ON t.`id` = a.`owner_id`
SET a.`owner_type` = 'RECEIPT', a.`owner_id` = t.`receipt_id`, a.`update_time` = NOW(), a.`update_by` = 'sys'
WHERE a.`owner_type` = 'SHOOT' AND a.`kind` = 2;

-- 待拍摄任务：型号已有完成的任务时跳过
UPDATE `shoot_task` p
JOIN (SELECT DISTINCT `tenant_id`, `asset_key` FROM `shoot_task` WHERE `status` = 2 AND `deleted_at` IS NULL) d
  ON d.`tenant_id` = p.`tenant_id` AND d.`asset_key` = p.`asset_key`
SET p.`status` = 3, p.`skip_reason` = '已有营销图', p.`update_time` = NOW(), p.`update_by` = 'sys'
WHERE p.`status` = 1 AND p.`deleted_at` IS NULL;

-- 同型号多条待拍摄：保留最早的一条，其余跳过；已传的营销图挂到保留的那条上
UPDATE `biz_attachment` a
JOIN `shoot_task` p ON p.`id` = a.`owner_id`
JOIN (SELECT `tenant_id`, `asset_key`, MIN(`id`) AS `keep_id` FROM `shoot_task`
      WHERE `status` = 1 AND `deleted_at` IS NULL GROUP BY `tenant_id`, `asset_key`) k
  ON k.`tenant_id` = p.`tenant_id` AND k.`asset_key` = p.`asset_key`
SET a.`owner_id` = k.`keep_id`, a.`update_time` = NOW(), a.`update_by` = 'sys'
WHERE a.`owner_type` = 'SHOOT' AND p.`status` = 1 AND p.`id` <> k.`keep_id`;

UPDATE `shoot_task` p
JOIN (SELECT `tenant_id`, `asset_key`, MIN(`id`) AS `keep_id` FROM `shoot_task`
      WHERE `status` = 1 AND `deleted_at` IS NULL GROUP BY `tenant_id`, `asset_key`) k
  ON k.`tenant_id` = p.`tenant_id` AND k.`asset_key` = p.`asset_key`
SET p.`status` = 3, p.`skip_reason` = CONCAT('合并到 ', k.`keep_id`), p.`update_time` = NOW(), p.`update_by` = 'sys'
WHERE p.`status` = 1 AND p.`deleted_at` IS NULL AND p.`id` <> k.`keep_id`;

-- ---------------------------------------------------------------- 已有附件写入素材索引

-- 供方发货：标注发货单的全部型号（作废的发货单不进素材库）
INSERT INTO `biz_attachment_model` (`tenant_id`, `attachment_id`, `asset_key`, `brand`, `model`)
SELECT DISTINCT a.`tenant_id`, a.`id`, LOWER(CONCAT(TRIM(i.`brand`), ' ', TRIM(i.`model`))), TRIM(i.`brand`), TRIM(i.`model`)
FROM `biz_attachment` a
JOIN `supplier_shipment` s ON s.`id` = a.`owner_id`
JOIN `supplier_shipment_item` i ON i.`shipment_id` = s.`id` AND i.`deleted_at` IS NULL
WHERE a.`owner_type` = 'SHIPMENT' AND a.`owner_id` > 0 AND a.`deleted_at` IS NULL AND s.`status` <> 3 AND s.`deleted_at` IS NULL
ON DUPLICATE KEY UPDATE `deleted_at` = NULL;

-- 验收入库：标注入库单的全部型号（冲销的入库单不进素材库）；上面已标注的视频跳过
INSERT INTO `biz_attachment_model` (`tenant_id`, `attachment_id`, `asset_key`, `brand`, `model`)
SELECT DISTINCT a.`tenant_id`, a.`id`, LOWER(CONCAT(TRIM(i.`brand`), ' ', TRIM(i.`model`))), TRIM(i.`brand`), TRIM(i.`model`)
FROM `biz_attachment` a
JOIN `purchase_receipt` r ON r.`id` = a.`owner_id`
JOIN `purchase_receipt_item` i ON i.`receipt_id` = r.`id` AND i.`deleted_at` IS NULL
WHERE a.`owner_type` = 'RECEIPT' AND a.`owner_id` > 0 AND a.`deleted_at` IS NULL AND r.`status` = 1 AND r.`deleted_at` IS NULL
  AND NOT EXISTS (SELECT 1 FROM `biz_attachment_model` m WHERE m.`attachment_id` = a.`id`)
ON DUPLICATE KEY UPDATE `deleted_at` = NULL;

-- 标注 → 素材：供方发货（仅内部）、验货记录（可发客户）
INSERT INTO `media_item` (`tenant_id`, `asset_key`, `brand`, `model`, `category`, `kind`, `source_table`, `source_id`, `can_send`)
SELECT a.`tenant_id`, m.`asset_key`, m.`brand`, m.`model`,
       IF(a.`owner_type` = 'SHIPMENT', 6, 7), IF(a.`kind` = 2, 2, 1), 'biz_attachment', a.`id`, IF(a.`owner_type` = 'SHIPMENT', 0, 1)
FROM `biz_attachment` a
JOIN `biz_attachment_model` m ON m.`attachment_id` = a.`id` AND m.`deleted_at` IS NULL
WHERE a.`owner_type` IN ('SHIPMENT', 'RECEIPT') AND a.`deleted_at` IS NULL AND a.`kind` IN (1, 2)
ON DUPLICATE KEY UPDATE `deleted_at` = NULL;

-- 询价回价的图片 → 供方实拍（仅内部）；回价的 PDF 报价单不算素材
INSERT INTO `media_item` (`tenant_id`, `asset_key`, `brand`, `model`, `category`, `kind`, `source_table`, `source_id`, `can_send`)
SELECT a.`tenant_id`, LOWER(CONCAT(TRIM(q.`brand`), ' ', TRIM(q.`model`))), TRIM(q.`brand`), TRIM(q.`model`), 4, 1, 'biz_attachment', a.`id`, 0
FROM `biz_attachment` a
JOIN `sourcing_quote` q ON q.`id` = a.`owner_id`
WHERE a.`owner_type` = 'SOURCING_QUOTE' AND a.`kind` = 1 AND a.`deleted_at` IS NULL AND TRIM(q.`model`) <> ''
ON DUPLICATE KEY UPDATE `deleted_at` = NULL;

-- 客户询盘的图片 → 客户来图（仅内部），标注询盘单的全部型号；Excel、PDF 询价清单不算素材
INSERT INTO `media_item` (`tenant_id`, `asset_key`, `brand`, `model`, `category`, `kind`, `source_table`, `source_id`, `can_send`)
SELECT DISTINCT a.`tenant_id`,
       LOWER(CONCAT(TRIM(i.`brand`), ' ', TRIM(COALESCE(NULLIF(i.`confirmed_model`, ''), i.`original_model`)))),
       TRIM(i.`brand`), TRIM(COALESCE(NULLIF(i.`confirmed_model`, ''), i.`original_model`)),
       3, 1, 'customer_inquiry_attachment', a.`id`, 0
FROM `customer_inquiry_attachment` a
JOIN `inquiry_item` i ON i.`customer_inquiry_id` = a.`customer_inquiry_id` AND i.`deleted_at` IS NULL
WHERE a.`deleted_at` IS NULL AND a.`content_type` LIKE 'image/%'
  AND TRIM(COALESCE(NULLIF(i.`confirmed_model`, ''), i.`original_model`)) <> ''
ON DUPLICATE KEY UPDATE `deleted_at` = NULL;
