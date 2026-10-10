-- 品类中英文简介（openspec add-category-description）：加中文简介字段，原简介改为英文简介；简介内容见 V1.2.50
ALTER TABLE `product_category`
    ADD COLUMN `description_zh` varchar(500) NOT NULL DEFAULT '' COMMENT '中文简介，选填，系统内显示' AFTER `description`,
    MODIFY COLUMN `description` varchar(500) NOT NULL DEFAULT '' COMMENT '英文简介，选填，独立站品类页等对外场景使用；属于品类本身，所有品牌下的商品共用';
