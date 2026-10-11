-- 品牌等级与中文简介（openspec seed-product-brands）
ALTER TABLE `product_brand`
    ADD COLUMN `brand_level` tinyint(2) NOT NULL DEFAULT 0 COMMENT '品牌等级（0-普通、1-常做、2-核心）；品牌下拉按等级、名称排序' AFTER `is_genuine`,
    ADD COLUMN `description_zh` varchar(500) NOT NULL DEFAULT '' COMMENT '中文简介，选填，系统内显示' AFTER `description`,
    MODIFY COLUMN `description` varchar(500) NOT NULL DEFAULT '' COMMENT '英文简介，选填，独立站品牌页等对外场景使用',
    ADD KEY `idx_brand_level` (`brand_level`);
