-- V1.2.1：品牌与品类简介、原产地清单订正（由 sql/build/sql/schema_v1.2.1.sql 迁移而来）
-- schema_v1.2.1.sql（依赖 schema_v1.2.sql；不修改 schema_v1.2.sql，可重复执行）

-- ============================
-- v1.2.1  品牌与品类简介、原产地清单订正
-- 1. product_brand    新增 description（品牌简介）
-- 2. product_category 新增 description（品类简介）
-- 3. 订正历史数据：原产地 USA -> United States（与统一国家清单的写法一致）
-- 变更说明见 openspec/changes/enrich-brand-category-info/
-- ============================

-- MySQL 8 的 ADD COLUMN 不支持 IF NOT EXISTS，用 information_schema 判断后再执行，保证可重复执行
SET @ddl = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'product_brand' AND column_name = 'description') = 0,
    'ALTER TABLE `product_brand` ADD COLUMN `description` varchar(500) NOT NULL DEFAULT '''' COMMENT ''品牌简介，选填，独立站品牌页使用'' AFTER `brand_color`',
    'SELECT 1'));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'product_category' AND column_name = 'description') = 0,
    'ALTER TABLE `product_category` ADD COLUMN `description` varchar(500) NOT NULL DEFAULT '''' COMMENT ''品类简介，选填，独立站品类页使用；属于品类本身，所有品牌下的商品共用'' AFTER `category_name`',
    'SELECT 1'));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 历史数据订正：与国家清单里的英文简称对齐
UPDATE `product_brand` SET `country` = 'United States' WHERE `country` = 'USA';

-- ============================
-- 回滚（需要时手工执行；会丢失已录入的简介，执行前先备份）
-- ============================
-- ALTER TABLE `product_brand` DROP COLUMN `description`;
-- ALTER TABLE `product_category` DROP COLUMN `description`;
-- UPDATE `product_brand` SET `country` = 'USA' WHERE `country` = 'United States';
