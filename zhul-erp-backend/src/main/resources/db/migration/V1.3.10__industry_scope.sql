-- 商品主数据按行业划分（openspec product-industry-scope）：
-- 1. 行业字典 industry，首个行业「工业控制」INDUSTRIAL_CONTROL；
-- 2. 租户、一级品类增加行业编码，品牌与行业多对多（product_brand_industry）；系列跟随品牌、商品跟随一级品类，不另存；
-- 3. 现有租户、一级品类、品牌全部设为工业控制。可重复执行。

INSERT INTO `dict_type` (`tenant_id`, `dict_type`, `dict_name`, `is_builtin`, `status`, `remark`, `create_by`, `update_by`)
SELECT 0, 'industry', '行业', 1, 1, '租户所属行业；品牌、一级品类按行业划分，租户只看到本行业的商品主数据', 'sys', 'sys' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `dict_type` d WHERE d.`dict_type` = 'industry' AND d.`tenant_id` = 0);

INSERT INTO `dict_item` (`tenant_id`, `dict_type_id`, `dict_type`, `item_code`, `item_name`, `item_name_en`, `item_value`, `sort_order`, `is_default`, `status`, `remark`, `create_by`, `update_by`)
SELECT 0, t.`id`, 'industry', 'INDUSTRIAL_CONTROL', '工业控制', 'Industrial Control', 'INDUSTRIAL_CONTROL', 1, 1, 1,
       'PLC、变频、伺服、传感器、低压电器、流体动力等工业自动化产品', 'sys', 'sys'
FROM `dict_type` t
WHERE t.`dict_type` = 'industry' AND t.`tenant_id` = 0
  AND NOT EXISTS (SELECT 1 FROM `dict_item` i WHERE i.`dict_type` = 'industry' AND i.`item_code` = 'INDUSTRIAL_CONTROL' AND i.`tenant_id` = 0);

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tenant' AND COLUMN_NAME = 'industry_code');
SET @s := IF(@c = 0, 'ALTER TABLE `tenant` ADD COLUMN `industry_code` varchar(32) NOT NULL DEFAULT '''' COMMENT ''所属行业编码（字典 industry），决定租户可见的品牌、品类、系列与商品'' AFTER `package_id`, ADD INDEX `idx_industry_code` (`industry_code`)', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_category' AND COLUMN_NAME = 'industry_code');
SET @s := IF(@c = 0, 'ALTER TABLE `product_category` ADD COLUMN `industry_code` varchar(32) NOT NULL DEFAULT '''' COMMENT ''所属行业编码（字典 industry）；一级品类必填，细分品类为空、跟随上级'' AFTER `parent_id`, ADD INDEX `idx_industry_code` (`industry_code`)', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

CREATE TABLE IF NOT EXISTS `product_brand_industry` (
  `id`            bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `brand_id`      bigint      NOT NULL COMMENT '品牌ID，关联 product_brand.id',
  `industry_code` varchar(32) NOT NULL COMMENT '行业编码（字典 industry）',
  `create_time`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `create_by`     varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
  `update_time`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `update_by`     varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_brand_industry` (`brand_id`, `industry_code`),
  KEY `idx_industry_code` (`industry_code`)
) ENGINE=InnoDB AUTO_INCREMENT=1000 DEFAULT CHARSET=utf8mb4 COMMENT='品牌所属行业（多对多）；系列跟随品牌';

UPDATE `tenant` SET `industry_code` = 'INDUSTRIAL_CONTROL', `update_time` = NOW(), `update_by` = 'sys' WHERE `industry_code` = '';
UPDATE `product_category` SET `industry_code` = 'INDUSTRIAL_CONTROL', `update_time` = NOW(), `update_by` = 'sys'
WHERE `tenant_id` = 0 AND `parent_id` IS NULL AND `industry_code` = '';
INSERT IGNORE INTO `product_brand_industry` (`brand_id`, `industry_code`)
SELECT b.`id`, 'INDUSTRIAL_CONTROL' FROM `product_brand` b WHERE b.`tenant_id` = 0;
