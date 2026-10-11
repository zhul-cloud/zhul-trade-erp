-- 营销内容按平台（openspec platform-marketing-content）：阿里国际站、中国制造网、社媒存新表，独立站沿用 product_seo；
-- 关键词库按平台分档；商品按平台「不发布」；公司启用哪些平台存 sys_config（平台模板，公司可覆盖）

CREATE TABLE IF NOT EXISTS `product_platform_content` (
    `id`                  bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`           int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `product_id`          bigint       NOT NULL            COMMENT '商品ID',
    `platform`            varchar(16)  NOT NULL DEFAULT '' COMMENT '平台（alibaba-阿里国际站、mic-中国制造网、social-社媒；独立站见 product_seo）',
    `lang`                varchar(8)   NOT NULL DEFAULT 'en' COMMENT '语言（en-英文）',
    `title`               varchar(128) NOT NULL DEFAULT '' COMMENT '标题（阿里国际站、中国制造网）',
    `selling_points`      text         NULL                COMMENT '卖点，每行一条（阿里国际站）',
    `detail`              mediumtext   NULL                COMMENT '商品详情 Markdown（中国制造网）',
    `linkedin_post`       text         NULL                COMMENT 'LinkedIn 帖子（社媒）',
    `facebook_post`       text         NULL                COMMENT 'Facebook 帖子（社媒）',
    `youtube_description` text         NULL                COMMENT 'YouTube 视频描述（社媒）',
    `import_id`           bigint       NULL                COMMENT '最近一次写入的上传记录ID',
    `deleted_at`          datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`           varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`           varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_platform_content` (`tenant_id`, `product_id`, `platform`, `lang`),
    KEY `idx_platform_content_product` (`product_id`),
    KEY `idx_platform_content_deleted` (`deleted_at`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000 DEFAULT CHARSET = utf8mb4 COMMENT = '商品平台营销内容（按公司；阿里国际站、中国制造网、社媒）';

CREATE TABLE IF NOT EXISTS `product_platform_keyword` (
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `product_id`  bigint       NOT NULL            COMMENT '商品ID',
    `platform`    varchar(16)  NOT NULL DEFAULT '' COMMENT '平台（alibaba-阿里国际站、mic-中国制造网）',
    `lang`        varchar(8)   NOT NULL DEFAULT 'en' COMMENT '语言（en-英文）',
    `keyword`     varchar(128) NOT NULL DEFAULT '' COMMENT '关键词',
    `tier`        tinyint(2)   NOT NULL DEFAULT 2  COMMENT '档位（1-核心、2-主要、3-长尾）',
    `sort_order`  int          NOT NULL DEFAULT 0  COMMENT '档内顺序',
    `import_id`   bigint       NULL                COMMENT '来源上传记录ID',
    `deleted_at`  datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_platform_keyword_tenant` (`tenant_id`),
    KEY `idx_platform_keyword_product` (`product_id`, `platform`, `lang`),
    KEY `idx_platform_keyword_deleted` (`deleted_at`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000 DEFAULT CHARSET = utf8mb4 COMMENT = '商品平台关键词库（按公司、按平台，按档位与档内顺序排序）';

CREATE TABLE IF NOT EXISTS `product_marketing_skip` (
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `product_id`  bigint       NOT NULL            COMMENT '商品ID',
    `platform`    varchar(16)  NOT NULL DEFAULT '' COMMENT '平台（alibaba-阿里国际站、mic-中国制造网、site-独立站、social-社媒）',
    `marked_at`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '标为不发布的时间',
    `marked_by`   varchar(32)  NOT NULL DEFAULT '' COMMENT '标为不发布的人',
    `deleted_at`  datetime     NULL                COMMENT '撤销时间（软删除），NULL表示当前标记有效',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_marketing_skip` (`tenant_id`, `product_id`, `platform`),
    KEY `idx_marketing_skip_product` (`product_id`),
    KEY `idx_marketing_skip_deleted` (`deleted_at`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000 DEFAULT CHARSET = utf8mb4 COMMENT = '商品按平台不发布标记（按公司；撤销后再标记复用同一行）';

ALTER TABLE `product_content_task`
    MODIFY COLUMN `en_at` datetime NULL COMMENT '独立站英文营销内容最近写入时间',
    MODIFY COLUMN `en_by` varchar(32) NOT NULL DEFAULT '' COMMENT '独立站英文营销内容最近写入人',
    MODIFY COLUMN `ru_at` datetime NULL COMMENT '独立站俄文营销内容最近写入时间',
    MODIFY COLUMN `ru_by` varchar(32) NOT NULL DEFAULT '' COMMENT '独立站俄文营销内容最近写入人',
    ADD COLUMN `alibaba_at` datetime NULL COMMENT '阿里国际站营销内容最近写入时间' AFTER `ru_by`,
    ADD COLUMN `alibaba_by` varchar(32) NOT NULL DEFAULT '' COMMENT '阿里国际站营销内容最近写入人' AFTER `alibaba_at`,
    ADD COLUMN `mic_at` datetime NULL COMMENT '中国制造网营销内容最近写入时间' AFTER `alibaba_by`,
    ADD COLUMN `mic_by` varchar(32) NOT NULL DEFAULT '' COMMENT '中国制造网营销内容最近写入人' AFTER `mic_at`,
    ADD COLUMN `social_at` datetime NULL COMMENT '社媒营销内容最近写入时间' AFTER `mic_by`,
    ADD COLUMN `social_by` varchar(32) NOT NULL DEFAULT '' COMMENT '社媒营销内容最近写入人' AFTER `social_at`;

INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_type`, `is_builtin`, `config_group`, `remark`, `create_by`, `update_by`)
SELECT 0, 'product.content.platforms', '营销平台', 'alibaba,mic,site,social', 'STRING', 1, 'product',
       '本公司做哪些平台的营销内容（alibaba-阿里国际站、mic-中国制造网、site-独立站、social-社媒，逗号分隔）', 'sys', 'sys'
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` c WHERE c.`tenant_id` = 0 AND c.`config_key` = 'product.content.platforms');
