-- V1.5.47：资料任务批量与海关编码（openspec content-ai-batch-hs）
-- 1. 品类默认海关编码：AI 生成资料时查不到型号的编码用它；细分品类没填时沿用上级品类的。
-- 2. 目的国进口关税参考：按海关编码前 6 位、国家维护（平台级，tenant_id 恒为 0），同一 6 位编码的品类共用。

ALTER TABLE `product_category`
    ADD COLUMN `hs_code` varchar(10) NOT NULL DEFAULT '' COMMENT '默认海关编码，只含数字 6~10 位；空表示未填（细分品类沿用上级品类）' AFTER `industry_code`;

CREATE TABLE `hs_tariff_ref`
(
    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`    int(11)      NOT NULL DEFAULT 0 COMMENT '租户ID（平台级，恒为 0）',
    `hs6`          char(6)      NOT NULL COMMENT '海关编码前 6 位',
    `country_code` char(2)      NOT NULL COMMENT '目的国，ISO 3166-1 两位国家码',
    `rate`         varchar(32)  NOT NULL DEFAULT '' COMMENT '进口关税（文字，如 5%、0%）；空表示未填',
    `note`         varchar(200) NOT NULL DEFAULT '' COMMENT '备注，如适用的自贸协定、附加税',
    `source`       varchar(300) NOT NULL DEFAULT '' COMMENT '来源（AI 补全时记录的网址或说明）',
    `checked_at`   date         NULL COMMENT '核对日期',
    `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`    varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`    varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_hs_tariff_ref` (`tenant_id`, `hs6`, `country_code`),
    KEY `idx_htr_tenant` (`tenant_id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1000
  DEFAULT CHARSET = utf8mb4 COMMENT ='目的国进口关税参考（平台级）：按海关编码前 6 位与国家，参考信息，不代替报关行意见';

-- 回退：ALTER TABLE `product_category` DROP COLUMN `hs_code`; DROP TABLE `hs_tariff_ref`;
