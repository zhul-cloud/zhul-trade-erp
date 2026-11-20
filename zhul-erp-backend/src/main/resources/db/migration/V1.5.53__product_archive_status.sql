-- 单据统一关联商品（openspec doc-product-link）：候选进池即生成商品，商品增加建档状态与并入目标
ALTER TABLE `product`
    ADD COLUMN `archive_status` tinyint(2)  NOT NULL DEFAULT 2  COMMENT '建档状态（1-候选、2-已建档、3-已并入、4-已驳回）' AFTER `status`,
    ADD COLUMN `brand_key`      varchar(80) NOT NULL DEFAULT '' COMMENT '品牌键（识别到品牌为 #品牌ID，否则为小写去空格的品牌原文；已并入的为 ~商品ID），与归一化型号唯一' AFTER `brand_id`,
    ADD COLUMN `merged_into_id` bigint      NULL               COMMENT '已并入时的主商品ID，关联product.id' AFTER `archive_status`,
    ADD KEY `idx_archive_status` (`archive_status`),
    ADD KEY `idx_merged_into_id` (`merged_into_id`);

UPDATE `product` SET `brand_key` = CONCAT('#', `brand_id`) WHERE `brand_key` = '';

-- 唯一键由「品牌ID + 归一化型号」改为「品牌键 + 归一化型号」：品牌没识别的候选商品 brand_id 都是 0，要按品牌原文区分
ALTER TABLE `product`
    DROP INDEX `uk_tenant_brand_mpn`,
    ADD UNIQUE KEY `uk_tenant_brandkey_mpn` (`tenant_id`, `brand_key`, `mpn_normalized`);

CREATE TABLE IF NOT EXISTS `product_merge` (
    `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`       int(11)      NOT NULL DEFAULT 0      COMMENT '租户ID（0-平台，商品合并是平台级操作）',
    `from_product_id` bigint       NOT NULL                COMMENT '被并入的商品ID，关联product.id',
    `to_product_id`   bigint       NOT NULL                COMMENT '主商品ID，关联product.id',
    `from_label`      varchar(200) NOT NULL DEFAULT ''     COMMENT '被并入商品的品牌 / 型号（合并时的快照）',
    `affected`        json         NULL                    COMMENT '各明细表改指向的条数，如 {"quotation_item":1}',
    `reason`          varchar(200) NOT NULL DEFAULT ''     COMMENT '合并原因',
    `operator_id`     varchar(32)  NOT NULL DEFAULT 'sys'  COMMENT '操作人',
    `operator_tenant_id` int(11)   NOT NULL DEFAULT 0      COMMENT '操作人所在租户',
    `deleted_at`      datetime     NULL                    COMMENT '软删除时间，NULL表示未删除（合并不能撤销，保留字段以符合规范）',
    `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`       varchar(32)  NOT NULL DEFAULT 'sys'  COMMENT '创建人',
    `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`       varchar(32)  NOT NULL DEFAULT 'sys'  COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_from_product` (`from_product_id`),
    KEY `idx_to_product` (`to_product_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品合并记录（候选并入已建档商品时，单据明细改指向主商品）';
