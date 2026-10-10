-- 商品内容方案（openspec add-product-content-plan）：多语言、公司 SEO/GEO 与 FAQ、内容任务

-- ---------------------------------------------------------------- 共享商品库：语言
ALTER TABLE `product_specification`
    ADD COLUMN `lang` varchar(8) NOT NULL DEFAULT 'en' COMMENT '语言（zh-中文、en-英文、ru-俄文）' AFTER `product_id`,
    ADD KEY `idx_product_spec_lang` (`product_id`, `lang`);

ALTER TABLE `product_application`
    ADD COLUMN `lang` varchar(8) NOT NULL DEFAULT 'en' COMMENT '语言（zh-中文、en-英文、ru-俄文）' AFTER `product_id`,
    ADD KEY `idx_product_app_lang` (`product_id`, `lang`);

CREATE TABLE IF NOT EXISTS `product_locale` (
    `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID（平台级，固定 0）',
    `product_id`    bigint       NOT NULL            COMMENT '商品ID',
    `lang`          varchar(8)   NOT NULL DEFAULT 'en' COMMENT '语言（zh-中文、en-英文、ru-俄文）',
    `spec_summary`  varchar(300) NOT NULL DEFAULT '' COMMENT '一句话规格摘要',
    `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`     varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`     varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_locale` (`product_id`, `lang`),
    KEY `idx_product_locale_tenant` (`tenant_id`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000 DEFAULT CHARSET = utf8mb4 COMMENT = '商品多语言文本（平台级）';

CREATE TABLE IF NOT EXISTS `product_relationship_note` (
    `id`               bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID（平台级，固定 0）',
    `relationship_id`  bigint       NOT NULL            COMMENT '型号关系ID',
    `lang`             varchar(8)   NOT NULL DEFAULT 'en' COMMENT '语言（zh-中文、en-英文、ru-俄文）',
    `note`             varchar(500) NOT NULL DEFAULT '' COMMENT '兼容说明',
    `create_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`        varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`        varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_relationship_note` (`relationship_id`, `lang`),
    KEY `idx_relationship_note_tenant` (`tenant_id`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000 DEFAULT CHARSET = utf8mb4 COMMENT = '型号关系说明多语言（平台级）';

-- ---------------------------------------------------------------- 公司内容：SEO/GEO 与 FAQ
CREATE TABLE IF NOT EXISTS `product_seo` (
    `id`                bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`         int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `product_id`        bigint        NOT NULL            COMMENT '商品ID',
    `lang`              varchar(8)    NOT NULL DEFAULT 'en' COMMENT '语言（zh-中文、en-英文、ru-俄文）',
    `seo_title`         varchar(120)  NOT NULL DEFAULT '' COMMENT 'SEO 标题',
    `meta_description`  varchar(320)  NOT NULL DEFAULT '' COMMENT 'SEO 描述',
    `long_description`  text          NULL                COMMENT '产品长描述',
    `geo_answer`        varchar(1000) NOT NULL DEFAULT '' COMMENT '首屏定义块（GEO 答案块）',
    `import_id`         bigint        NULL                COMMENT '最近一次写入的上传记录ID',
    `deleted_at`        datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`         varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`         varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_seo` (`tenant_id`, `product_id`, `lang`),
    KEY `idx_product_seo_product` (`product_id`),
    KEY `idx_product_seo_deleted` (`deleted_at`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000 DEFAULT CHARSET = utf8mb4 COMMENT = '商品 SEO/GEO 内容（按公司）';

CREATE TABLE IF NOT EXISTS `product_seo_faq` (
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `product_id`  bigint       NOT NULL            COMMENT '商品ID',
    `lang`        varchar(8)   NOT NULL DEFAULT 'en' COMMENT '语言（zh-中文、en-英文、ru-俄文）',
    `question`    varchar(256) NOT NULL DEFAULT '' COMMENT '问题',
    `answer`      text         NULL                COMMENT '答案（可含本公司质保、发货、联系方式）',
    `sort_order`  int          NOT NULL DEFAULT 0  COMMENT '排序',
    `import_id`   bigint       NULL                COMMENT '来源上传记录ID',
    `deleted_at`  datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_seo_faq_tenant` (`tenant_id`),
    KEY `idx_seo_faq_product` (`product_id`, `lang`),
    KEY `idx_seo_faq_deleted` (`deleted_at`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000 DEFAULT CHARSET = utf8mb4 COMMENT = '商品 FAQ（按公司）';

-- ---------------------------------------------------------------- 内容任务与上传记录（按公司）
CREATE TABLE IF NOT EXISTS `product_content_task` (
    `id`             bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`      int(11)     NOT NULL DEFAULT 0  COMMENT '租户ID',
    `product_id`     bigint      NOT NULL            COMMENT '商品ID',
    `status`         tinyint(2)  NOT NULL DEFAULT 1  COMMENT '状态（1-待生成、2-进行中、3-已完成）',
    `zh_at`          datetime    NULL                COMMENT '中文最近写入时间',
    `zh_by`          varchar(32) NOT NULL DEFAULT '' COMMENT '中文最近写入人',
    `en_at`          datetime    NULL                COMMENT '英文最近写入时间',
    `en_by`          varchar(32) NOT NULL DEFAULT '' COMMENT '英文最近写入人',
    `ru_at`          datetime    NULL                COMMENT '俄文最近写入时间',
    `ru_by`          varchar(32) NOT NULL DEFAULT '' COMMENT '俄文最近写入人',
    `downloaded_at`  datetime    NULL                COMMENT '最近下载任务包时间',
    `deleted_at`     datetime    NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`    datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`      varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`    datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`      varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_content_task` (`tenant_id`, `product_id`),
    KEY `idx_content_task_product` (`product_id`),
    KEY `idx_content_task_status` (`status`),
    KEY `idx_content_task_deleted` (`deleted_at`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000 DEFAULT CHARSET = utf8mb4 COMMENT = '商品内容任务（按公司；没有记录的商品视为待生成）';

CREATE TABLE IF NOT EXISTS `product_content_import` (
    `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `task_id`       bigint       NOT NULL            COMMENT '内容任务ID',
    `product_id`    bigint       NOT NULL            COMMENT '商品ID',
    `lang`          varchar(8)   NOT NULL DEFAULT 'en' COMMENT '语言（zh-中文、en-英文、ru-俄文）',
    `file_name`     varchar(255) NOT NULL DEFAULT '' COMMENT '上传文件名',
    `raw_text`      mediumtext   NULL                COMMENT '上传原文（追溯用）',
    `summary`       varchar(1000) NOT NULL DEFAULT '' COMMENT '写入摘要（各块条数）',
    `confirmed_by`  varchar(32)  NOT NULL DEFAULT '' COMMENT '确认人',
    `deleted_at`    datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（确认时间）',
    `create_by`     varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`     varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_content_import_tenant` (`tenant_id`),
    KEY `idx_content_import_task` (`task_id`),
    KEY `idx_content_import_product` (`product_id`),
    KEY `idx_content_import_deleted` (`deleted_at`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000 DEFAULT CHARSET = utf8mb4 COMMENT = '商品内容上传记录（按公司）';

-- ---------------------------------------------------------------- 菜单：商品资料 → 内容任务；按钮：商品内容维护
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
              SELECT 100100 AS id, 100004 AS pid, 'RS2100100' AS code, '内容任务' AS name, 2 AS type, 3 AS sort, 'file-text' AS li, '' AS lsi, '' AS di, '' AS dsi,
                     '/product/content-tasks' AS path, '' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
    UNION ALL SELECT 110202, 100100, 'RS3110202', '商品内容维护', 3, 1, '', '', '', '', '', 'product:content:edit', 1, '', 'sys', 'sys'
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.id);

-- 商品资料：商品、商品候选、内容任务、品牌、品类、系列
UPDATE `resource` SET `sort` = 4, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100031;
UPDATE `resource` SET `sort` = 5, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100032;
UPDATE `resource` SET `sort` = 6, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100033;

UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(`menu_ids`, '$', 100100), '$', 110202)
WHERE `name` IN ('标准版', '旗舰版') AND NOT JSON_CONTAINS(`menu_ids`, '100100');

-- 能看「商品」的角色可以看「内容任务」；维护按钮只授给内置管理员
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, s.`id`, s.`code`
FROM `role_resource` x
JOIN `resource` s ON s.`id` = 100100
WHERE x.`resource_id` = 100034
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = s.`id`);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` IN (100100, 110202)
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);
