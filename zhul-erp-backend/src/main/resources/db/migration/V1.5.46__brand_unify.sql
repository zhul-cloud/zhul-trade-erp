-- V1.5.46：品牌统一（openspec brand-unify）
-- 1. 「不是品牌」的写法：平台账号标记后不再出现在待确认品牌里（平台级，tenant_id 恒为 0）。
-- 2. 存量单据的品牌写法按品牌库与别名统一成正式品牌名：询盘型号、销售订单、采购需求、采购订单，
--    以及还没发给客户的报价单草稿 / 修改中版本、PI 草稿 / 修改中版本。已发给客户的报价单、PI 不改。
-- 3. 品牌为空的单据行按商品档案补上品牌（订单行自身的商品档案 → 来源询盘型号的商品档案）；查不到的保持为空。
-- 只是把同一品牌的写法换成正式名，不更新 update_time：列表按更新时间排序，不应因此全部挪到最前。

CREATE TABLE `brand_text_ignored`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)      NOT NULL DEFAULT 0 COMMENT '租户ID（平台级，恒为 0）',
    `text_key`    varchar(128) NOT NULL COMMENT '比较键：去首尾空格、转小写',
    `text`        varchar(128) NOT NULL DEFAULT '' COMMENT '标记时的写法原文',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_brand_text_ignored` (`tenant_id`, `text_key`),
    KEY `idx_bti_tenant` (`tenant_id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1000
  DEFAULT CHARSET = utf8mb4 COMMENT ='不是品牌的写法（平台级）：不再出现在待确认品牌里，单据保持原样';

-- 正式品牌名与别名 → 正式品牌名（比较键：去首尾空格、转小写）
CREATE TEMPORARY TABLE `tmp_brand_name_map`
(
    `k` varchar(128) NOT NULL,
    `n` varchar(128) NOT NULL,
    PRIMARY KEY (`k`)
) DEFAULT CHARSET = utf8mb4;

INSERT IGNORE INTO `tmp_brand_name_map` (`k`, `n`)
SELECT LOWER(TRIM(`brand_name`)), `brand_name`
FROM `product_brand`
WHERE `tenant_id` = 0 AND `deleted_at` IS NULL;

INSERT IGNORE INTO `tmp_brand_name_map` (`k`, `n`)
SELECT a.`alias_key`, b.`brand_name`
FROM `product_brand_alias` a
    JOIN `product_brand` b ON b.`id` = a.`brand_id` AND b.`deleted_at` IS NULL
WHERE a.`tenant_id` = 0;

UPDATE `inquiry_item` t JOIN `tmp_brand_name_map` m ON m.`k` = LOWER(TRIM(t.`brand`))
SET t.`brand` = m.`n`
WHERE t.`deleted_at` IS NULL AND BINARY t.`brand` <> BINARY m.`n`;

UPDATE `sales_order_item` t JOIN `tmp_brand_name_map` m ON m.`k` = LOWER(TRIM(t.`brand`))
SET t.`brand` = m.`n`
WHERE t.`deleted_at` IS NULL AND BINARY t.`brand` <> BINARY m.`n`;

UPDATE `purchase_requirement` t JOIN `tmp_brand_name_map` m ON m.`k` = LOWER(TRIM(t.`brand`))
SET t.`brand` = m.`n`
WHERE t.`deleted_at` IS NULL AND BINARY t.`brand` <> BINARY m.`n`;

UPDATE `purchase_order_item` t JOIN `tmp_brand_name_map` m ON m.`k` = LOWER(TRIM(t.`brand`))
SET t.`brand` = m.`n`
WHERE t.`deleted_at` IS NULL AND BINARY t.`brand` <> BINARY m.`n`;

-- 报价单：只改草稿与修改中的版本
UPDATE `quotation_item` t
    JOIN `quotation` q ON q.`id` = t.`quotation_id`
    JOIN `tmp_brand_name_map` m ON m.`k` = LOWER(TRIM(t.`brand`))
SET t.`brand` = m.`n`
WHERE t.`deleted_at` IS NULL AND q.`deleted_at` IS NULL AND BINARY t.`brand` <> BINARY m.`n`
  AND ((q.`status` = 1 AND t.`is_current` = 1)
    OR (q.`editing_version_no` IS NOT NULL AND t.`version_no` = q.`editing_version_no`));

-- PI：只改正在编辑的版本（没有转订单、作废、关闭）
UPDATE `proforma_invoice_item` t
    JOIN `proforma_invoice_version` v ON v.`id` = t.`version_id`
    JOIN `proforma_invoice` p ON p.`id` = t.`pi_id`
    JOIN `tmp_brand_name_map` m ON m.`k` = LOWER(TRIM(t.`brand`))
SET t.`brand` = m.`n`
WHERE t.`deleted_at` IS NULL AND p.`deleted_at` IS NULL AND BINARY t.`brand` <> BINARY m.`n`
  AND v.`version_no` = p.`editing_version_no` AND p.`status` NOT IN (3, 4, 5);

DROP TEMPORARY TABLE `tmp_brand_name_map`;

-- 品牌为空：按商品档案补上
UPDATE `sales_order_item` s
    LEFT JOIN `inquiry_item` i ON i.`id` = s.`inquiry_item_id`
    JOIN `product` p ON p.`id` = COALESCE(s.`product_id`, i.`product_id`) AND p.`deleted_at` IS NULL
    JOIN `product_brand` b ON b.`id` = p.`brand_id` AND b.`deleted_at` IS NULL
SET s.`brand` = b.`brand_name`
WHERE s.`deleted_at` IS NULL AND TRIM(s.`brand`) = '';

UPDATE `inquiry_item` i
    JOIN `product` p ON p.`id` = i.`product_id` AND p.`deleted_at` IS NULL
    JOIN `product_brand` b ON b.`id` = p.`brand_id` AND b.`deleted_at` IS NULL
SET i.`brand` = b.`brand_name`
WHERE i.`deleted_at` IS NULL AND TRIM(i.`brand`) = '';

UPDATE `purchase_requirement` r JOIN `sales_order_item` s ON s.`id` = r.`so_item_id` AND s.`tenant_id` = r.`tenant_id`
SET r.`brand` = s.`brand`
WHERE r.`deleted_at` IS NULL AND TRIM(r.`brand`) = '' AND TRIM(s.`brand`) <> '';

UPDATE `purchase_order_item` o JOIN `sales_order_item` s ON s.`id` = o.`so_item_id` AND s.`tenant_id` = o.`tenant_id`
SET o.`brand` = s.`brand`
WHERE o.`deleted_at` IS NULL AND TRIM(o.`brand`) = '' AND TRIM(s.`brand`) <> '';

-- 回退：DROP TABLE `brand_text_ignored`；单据品牌写法的统一与补空不回退（只是把同一品牌的写法换成正式名、补上商品档案的品牌）。
