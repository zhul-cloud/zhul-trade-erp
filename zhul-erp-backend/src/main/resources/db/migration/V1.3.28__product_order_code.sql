-- V1.3.28：型号是唯一的建档编码，订货号（物料号、料号、Order No.、Article No.）作为辅助识别。
-- 西门子这类订货号本身就是行业通用编码的品牌，订货号即型号，不拆。可回退：删表、删两列即可，型号与建档数据不受影响。

CREATE TABLE IF NOT EXISTS `product_order_code`
(
    `id`          bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID（0=平台级共享，本表数据均为0，与商品一致）',
    `product_id`  bigint(20)   NOT NULL DEFAULT 0  COMMENT '商品ID，关联product.id',
    `brand_id`    bigint(20)   NOT NULL DEFAULT 0  COMMENT '品牌ID，关联product_brand.id（冗余，按品牌查订货号）',
    `code`        varchar(64)  NOT NULL DEFAULT '' COMMENT '订货号原文，如 1025994、3AXD50000038965',
    `code_key`    varchar(64)  NOT NULL DEFAULT '' COMMENT '比较键：按型号规则归一化（去空格与符号、转小写）；同一品牌下未删除的比较键唯一（应用层校验）',
    `deleted_at`  datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_brand_code` (`brand_id`, `code_key`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品订货号表（辅助识别，一个商品可有多个）';

ALTER TABLE `inquiry_item`
    ADD COLUMN `order_code` varchar(255) NOT NULL DEFAULT '' COMMENT '订货号（物料号、料号），辅助识别；多个用英文逗号分隔' AFTER `confirmed_model`;

ALTER TABLE `product_candidate`
    ADD COLUMN `order_code` varchar(255) NOT NULL DEFAULT '' COMMENT '订货号（物料号、料号），建档时带到商品；多个用英文逗号分隔' AFTER `mpn_normalized`;
