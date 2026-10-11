-- 商品内容规范 v2（openspec content-schema-v2）：规格证据、资料待确认项、关键词总库与平台选用、平台与独立站新字段；
-- 按平台存的关键词迁移成总库（同词不分大小写合并、档位取最高）+ 平台选用，旧表停用

ALTER TABLE `product_specification`
    ADD COLUMN `condition_text` varchar(256) NOT NULL DEFAULT '' COMMENT '条件/范围（v2 资料包）' AFTER `spec_unit`,
    ADD COLUMN `evidence` varchar(64) NOT NULL DEFAULT '' COMMENT '证据状态（官方确认、用户提供、第三方信息、应用推断、待确认等，原文）' AFTER `condition_text`,
    ADD COLUMN `source_note` varchar(256) NOT NULL DEFAULT '' COMMENT '证据来源说明（来源编号与名称）' AFTER `evidence`;

CREATE TABLE IF NOT EXISTS `product_fact_note` (
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID（平台级，固定 0）',
    `product_id`  bigint       NOT NULL            COMMENT '商品ID',
    `kind`        tinyint(2)   NOT NULL DEFAULT 1  COMMENT '类型（1-关键未知字段、2-冲突与待确认）',
    `item`        varchar(256) NOT NULL DEFAULT '' COMMENT '项目',
    `status_text` varchar(500) NOT NULL DEFAULT '' COMMENT '当前状态',
    `impact`      varchar(500) NOT NULL DEFAULT '' COMMENT '对发布的影响 / 说明',
    `sort_order`  int          NOT NULL DEFAULT 0  COMMENT '顺序',
    `import_id`   bigint       NULL                COMMENT '来源上传记录ID',
    `deleted_at`  datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_fact_note_tenant` (`tenant_id`),
    KEY `idx_fact_note_product` (`product_id`),
    KEY `idx_fact_note_deleted` (`deleted_at`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000 DEFAULT CHARSET = utf8mb4 COMMENT = '商品资料待确认项（共享；每次导入 v2 资料包整份替换）';

CREATE TABLE IF NOT EXISTS `product_keyword` (
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `product_id`  bigint       NOT NULL            COMMENT '商品ID',
    `lang`        varchar(8)   NOT NULL DEFAULT 'en' COMMENT '语言（en-英文）',
    `keyword`     varchar(128) NOT NULL DEFAULT '' COMMENT '关键词',
    `keyword_key` varchar(128) NOT NULL DEFAULT '' COMMENT '关键词小写键（去首尾空格），总库内唯一',
    `tier`        tinyint(2)   NOT NULL DEFAULT 2  COMMENT '档位（1-核心、2-主要、3-长尾）',
    `category`    varchar(64)  NOT NULL DEFAULT '' COMMENT '分类（如核心词、规格属性词、采购长尾词）',
    `intent`      varchar(128) NOT NULL DEFAULT '' COMMENT '搜索意图',
    `basis`       varchar(128) NOT NULL DEFAULT '' COMMENT '事实依据（事实编号等）',
    `sort_order`  int          NOT NULL DEFAULT 0  COMMENT '库内顺序',
    `import_id`   bigint       NULL                COMMENT '来源上传记录ID',
    `deleted_at`  datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_keyword_tenant` (`tenant_id`),
    KEY `idx_keyword_product` (`product_id`, `lang`),
    KEY `idx_keyword_deleted` (`deleted_at`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000 DEFAULT CHARSET = utf8mb4 COMMENT = '商品关键词总库（按公司、按型号一份；各平台从中选用）';

CREATE TABLE IF NOT EXISTS `product_keyword_use` (
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `product_id`  bigint       NOT NULL            COMMENT '商品ID',
    `keyword_id`  bigint       NOT NULL            COMMENT '关键词总库ID',
    `platform`    varchar(16)  NOT NULL DEFAULT '' COMMENT '平台（alibaba、mic、site、social）',
    `sort_order`  int          NOT NULL DEFAULT 0  COMMENT '该平台内顺序',
    `deleted_at`  datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_keyword_use_tenant` (`tenant_id`),
    KEY `idx_keyword_use_product` (`product_id`, `platform`),
    KEY `idx_keyword_use_keyword` (`keyword_id`),
    KEY `idx_keyword_use_deleted` (`deleted_at`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000 DEFAULT CHARSET = utf8mb4 COMMENT = '平台选用的关键词（按公司；引用关键词总库）';

ALTER TABLE `product_platform_content`
    ADD COLUMN `title_alternates` text NULL COMMENT '备选标题（JSON：[{title, angle}]）' AFTER `title`,
    ADD COLUMN `attributes` text NULL COMMENT '产品属性（JSON：[{name, value}]）' AFTER `selling_points`,
    ADD COLUMN `faqs` text NULL COMMENT '平台 FAQ（JSON：[{question, answer}]）' AFTER `detail`,
    ADD COLUMN `cta` varchar(500) NOT NULL DEFAULT '' COMMENT '询盘引导语' AFTER `faqs`,
    ADD COLUMN `posts` mediumtext NULL COMMENT '社媒帖子（JSON：[{channel, label, text}]）' AFTER `youtube_description`,
    ADD COLUMN `videos` mediumtext NULL COMMENT '视频方案（JSON：[{label, markdown}]）' AFTER `posts`;

ALTER TABLE `product_seo`
    ADD COLUMN `h1` varchar(255) NOT NULL DEFAULT '' COMMENT 'H1' AFTER `meta_description`,
    ADD COLUMN `url_slug` varchar(255) NOT NULL DEFAULT '' COMMENT 'URL 别名（建议）' AFTER `h1`,
    ADD COLUMN `alt_texts` text NULL COMMENT '图片说明文字（JSON：[{image, alt}]）' AFTER `geo_answer`;

-- 关键词迁移：同一公司、商品、语言下同词（小写、去首尾空格）合并成一个，档位取最高，文字取最高档里最先出现的写法
INSERT INTO `product_keyword` (`tenant_id`, `product_id`, `lang`, `keyword`, `keyword_key`, `tier`, `sort_order`, `import_id`, `create_by`, `update_by`)
SELECT g.tenant_id, g.product_id, g.lang, g.keyword, g.kkey, g.tier,
       ROW_NUMBER() OVER (PARTITION BY g.tenant_id, g.product_id, g.lang ORDER BY g.tier, g.first_sort, g.first_id),
       g.import_id, 'sys', 'sys'
FROM (
    SELECT k.tenant_id, k.product_id, k.lang, LOWER(TRIM(k.keyword)) AS kkey, k.keyword, k.tier,
           k.sort_order AS first_sort, k.id AS first_id, k.import_id,
           ROW_NUMBER() OVER (PARTITION BY k.tenant_id, k.product_id, k.lang, LOWER(TRIM(k.keyword))
                              ORDER BY k.tier, k.sort_order, k.id) AS rn
    FROM `product_platform_keyword` k
    WHERE k.deleted_at IS NULL
) g
WHERE g.rn = 1
  AND NOT EXISTS (SELECT 1 FROM `product_keyword` x WHERE x.tenant_id = g.tenant_id AND x.product_id = g.product_id
                  AND x.lang = g.lang AND x.keyword_key = g.kkey AND x.deleted_at IS NULL);

-- 原来的平台关系变成选用（同一平台里大小写不同的同一个词只留一条）
INSERT INTO `product_keyword_use` (`tenant_id`, `product_id`, `keyword_id`, `platform`, `sort_order`, `create_by`, `update_by`)
SELECT u.tenant_id, u.product_id, u.keyword_id, u.platform,
       ROW_NUMBER() OVER (PARTITION BY u.tenant_id, u.product_id, u.platform ORDER BY u.tier, u.sort_order, u.id), 'sys', 'sys'
FROM (
    SELECT k.id, k.tenant_id, k.product_id, k.platform, k.tier, k.sort_order, pk.id AS keyword_id,
           ROW_NUMBER() OVER (PARTITION BY pk.id, k.platform ORDER BY k.tier, k.sort_order, k.id) AS rn
    FROM `product_platform_keyword` k
    JOIN `product_keyword` pk ON pk.tenant_id = k.tenant_id AND pk.product_id = k.product_id AND pk.lang = k.lang
         AND pk.keyword_key = LOWER(TRIM(k.keyword)) AND pk.deleted_at IS NULL
    WHERE k.deleted_at IS NULL
) u
WHERE u.rn = 1
  AND NOT EXISTS (SELECT 1 FROM `product_keyword_use` x WHERE x.keyword_id = u.keyword_id AND x.platform = u.platform
                  AND x.deleted_at IS NULL);

ALTER TABLE `product_platform_keyword` COMMENT = '已停用：关键词改为总库（product_keyword）+ 平台选用（product_keyword_use），本表只保留历史数据';
