-- 独立站发布（openspec website-publish 设计第 4 条）：
-- 1. 站点 website_site、站点凭据 website_site_secret（AES-256-GCM 密文）、品类对照 website_category_map、
--    站点商品 website_product、导入与比对任务 website_site_job、发布单 website_release 及其商品 website_release_item、步骤 website_release_step；
-- 2. 发品任务 listing_task 加 site_id、发布前状态、发布单、待确认与确认字段，唯一键扩展为「商品 + 平台 + 站点」，新增状态 4-发布中、5-已上线待确认；
-- 3. 菜单「运营管理 → 站点管理」与按钮权限「修改站点」「发布到站点」，排在素材管理之后。
-- 回退：本迁移只新增表、列与菜单；回退版本时新表与新列可以保留不用，但已经产生「发布中」「已上线待确认」的任务要先处理完。
CREATE TABLE `website_site` (
    `id`                 bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`          int(11)       NOT NULL DEFAULT 0 COMMENT '租户ID',
    `name`               varchar(50)   NOT NULL DEFAULT '' COMMENT '站点名称，本公司内唯一',
    `url`                varchar(200)  NOT NULL DEFAULT '' COMMENT '网址：https:// 开头、不带路径、不带结尾斜杠，全平台唯一',
    `languages`          json          NULL COMMENT '语言：[{"code":"en","prefix":""},{"code":"ru","prefix":"/ru"}]，第一种为默认语言',
    `path_template`      varchar(200)  NOT NULL DEFAULT '/products/{slug}/' COMMENT '商品页地址模板，包含 {slug}',
    `publish_mode`       tinyint(2)    NOT NULL DEFAULT 1 COMMENT '发布方式（1-自动发布、2-只复制）',
    `repo_url`           varchar(300)  NOT NULL DEFAULT '' COMMENT '代码仓库地址（https://，不带账号）',
    `repo_branch`        varchar(100)  NOT NULL DEFAULT 'main' COMMENT '代码仓库分支',
    `deploy_host`        varchar(200)  NOT NULL DEFAULT '' COMMENT '部署主机',
    `deploy_port`        int(11)       NOT NULL DEFAULT 22 COMMENT '部署 SSH 端口',
    `deploy_user`        varchar(100)  NOT NULL DEFAULT '' COMMENT '部署用户',
    `deploy_path`        varchar(300)  NOT NULL DEFAULT '' COMMENT '部署目标目录',
    `product_scope`      json          NULL COMMENT '商品范围：{"brandIds":[],"categoryIds":[]}，空表示本公司全部商品；品类含下级',
    `non_genuine_brands` json          NULL COMMENT '非正品品牌：品牌ID数组，这些品牌的商品在站点上带非正品标记',
    `price_factor`       decimal(6,2)  NOT NULL DEFAULT 1.05 COMMENT '售价系数（0.5–10）：美元价 = 最低回价 × 系数 ÷ 美元汇率',
    `currency_code`      varchar(3)    NOT NULL DEFAULT 'USD' COMMENT '站点售价币种',
    `need_purge`         tinyint(1)    NOT NULL DEFAULT 0 COMMENT '是否需要人工清缓存（0-否、1-是）',
    `purge_note`         varchar(500)  NOT NULL DEFAULT '' COMMENT '清缓存操作说明，确认上线时显示',
    `takeover_status`    tinyint(2)    NOT NULL DEFAULT 1 COMMENT '接管状态（1-接管中、2-以ERP为准）',
    `takeover_by`        varchar(32)   NOT NULL DEFAULT '' COMMENT '开始以ERP为准的确认人',
    `takeover_at`        datetime      NULL COMMENT '开始以ERP为准的时间',
    `status`             tinyint(2)    NOT NULL DEFAULT 1 COMMENT '状态（0-停用、1-启用）',
    `deleted_at`         datetime      NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`          varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`          varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_url` (`url`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='独立站站点：网址、语言、发布方式、仓库与部署、商品范围、售价规则';

CREATE TABLE `website_site_secret` (
    `id`          bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)       NOT NULL DEFAULT 0 COMMENT '租户ID',
    `site_id`     bigint(20)    NOT NULL DEFAULT 0 COMMENT '站点ID，关联website_site.id',
    `secret_type` varchar(16)   NOT NULL DEFAULT '' COMMENT '凭据类型（repo_token-仓库令牌、deploy_key-部署私钥、indexnow_key-IndexNow密钥）',
    `cipher_text` text          NULL COMMENT '密文（AES-256-GCM，AesUtils），不存明文',
    `last4`       varchar(4)    NOT NULL DEFAULT '' COMMENT '明文末四位，页面显示「已配置 ····末四位」',
    `create_time` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_site_type` (`site_id`, `secret_type`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站点凭据：仓库令牌、部署私钥、IndexNow 密钥，加密保存';

CREATE TABLE `website_category_map` (
    `id`            bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     int(11)       NOT NULL DEFAULT 0 COMMENT '租户ID',
    `site_id`       bigint(20)    NOT NULL DEFAULT 0 COMMENT '站点ID，关联website_site.id',
    `category_id`   bigint(20)    NOT NULL DEFAULT 0 COMMENT 'ERP品类ID（一级或二级），关联product_category.id',
    `site_category` varchar(64)   NOT NULL DEFAULT '' COMMENT '站点品类代码，如 controllers、drives',
    `create_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`     varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`     varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_site_category` (`site_id`, `category_id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_category_id` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站点品类对照：ERP品类 → 站点品类代码；二级没设置时用一级';

CREATE TABLE `website_product` (
    `id`                bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`         int(11)       NOT NULL DEFAULT 0 COMMENT '租户ID',
    `site_id`           bigint(20)    NOT NULL DEFAULT 0 COMMENT '站点ID，关联website_site.id',
    `product_id`        bigint(20)    NOT NULL DEFAULT 0 COMMENT '商品ID，关联product.id（共享商品库）',
    `listing_task_id`   bigint(20)    NOT NULL DEFAULT 0 COMMENT '发品任务ID，关联listing_task.id',
    `slug`              varchar(200)  NOT NULL DEFAULT '' COMMENT '链接别名，与站点按品牌、系列、型号算出的一致；站内唯一由发布前检查保证（冲突时不能发布），不用唯一索引',
    `slug_locked`       tinyint(1)    NOT NULL DEFAULT 0 COMMENT '链接别名是否锁定（0-否、1-是）：发布或导入后锁定，不再随品牌型号变化',
    `manual_price`      decimal(18,2) NULL COMMENT '手工售价（站点币种），NULL表示按规则计算',
    `calc_price`        decimal(18,2) NULL COMMENT '计算售价（站点币种，HALF_UP 保留2位），NULL表示没有可用回价',
    `price_source`      json          NULL COMMENT '售价来源：{"type":"quote|manual|import","quoteId":1,"unitPriceCny":"100.00","factor":"1.05","rate":"7.100000","rateDate":"2026-11-27"}',
    `site_status`       varchar(16)   NOT NULL DEFAULT '' COMMENT '站点状态（instock、discont、legacy），空表示按商品生命周期',
    `original_content`  json          NULL COMMENT '站点原有内容：导入时站点上的全部字段原样保存',
    `imported_at`       datetime      NULL COMMENT '导入时间，NULL表示不是导入的',
    `erp_fields`        json          NULL COMMENT '改用ERP内容的字段名数组',
    `live_urls`         json          NULL COMMENT '上线链接：{"en":"https://…","ru":"https://…"}',
    `last_snapshot`     json          NULL COMMENT '上次发布的站点数据快照，用于判断内容有更新',
    `last_release_id`   bigint(20)    NOT NULL DEFAULT 0 COMMENT '上次发布单ID，关联website_release.id',
    `deleted_at`        datetime      NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`         varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`         varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_site_product` (`site_id`, `product_id`),
    KEY `idx_site_slug` (`site_id`, `slug`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_listing_task_id` (`listing_task_id`),
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站点商品：一个商品在一个站点上的链接别名、售价、原有内容与上次发布快照';

CREATE TABLE `website_site_job` (
    `id`           bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`    int(11)       NOT NULL DEFAULT 0 COMMENT '租户ID',
    `site_id`      bigint(20)    NOT NULL DEFAULT 0 COMMENT '站点ID，关联website_site.id',
    `job_type`     varchar(16)   NOT NULL DEFAULT '' COMMENT '任务类型（import-导入现有商品、compare-一致性比对）',
    `job_no`       varchar(64)   NOT NULL DEFAULT '' COMMENT '发给发布服务的任务号，回调按它查找',
    `status`       tinyint(2)    NOT NULL DEFAULT 1 COMMENT '状态（1-执行中、2-已完成、3-失败、4-已确认）',
    `result`       json          NULL COMMENT '发布服务返回的结果（导入：站点商品；比对：一致数、差异、只在站点或ERP的商品）',
    `accepted`     json          NULL COMMENT '比对差异的确认接受记录：[{"model":"…","field":"…","reason":"…","by":"…","at":"…"}]',
    `error`        varchar(500)  NOT NULL DEFAULT '' COMMENT '失败原因（已去掉凭据）',
    `started_at`   datetime      NULL COMMENT '开始时间',
    `ended_at`     datetime      NULL COMMENT '结束时间',
    `create_time`  datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`    varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`  datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`    varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_job_no` (`job_no`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_site_type` (`site_id`, `job_type`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站点导入与比对任务：发布服务异步执行，结果留待确认';

CREATE TABLE `website_release` (
    `id`            bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     int(11)       NOT NULL DEFAULT 0 COMMENT '租户ID',
    `site_id`       bigint(20)    NOT NULL DEFAULT 0 COMMENT '站点ID，关联website_site.id',
    `release_no`    varchar(32)   NOT NULL DEFAULT '' COMMENT '发布单号：WP + 编号规则',
    `status`        tinyint(2)    NOT NULL DEFAULT 1 COMMENT '状态（1-排队中、2-执行中、3-已完成、4-失败、5-部署失败）',
    `item_count`    int(11)       NOT NULL DEFAULT 0 COMMENT '商品数',
    `job_no`        varchar(64)   NOT NULL DEFAULT '' COMMENT '发给发布服务的任务号（每次提交或重试一个）',
    `started_at`    datetime      NULL COMMENT '开始时间',
    `ended_at`      datetime      NULL COMMENT '结束时间',
    `commit_sha`    varchar(40)   NOT NULL DEFAULT '' COMMENT '站点仓库提交号',
    `failed_step`   varchar(16)   NOT NULL DEFAULT '' COMMENT '失败的步骤',
    `error`         varchar(500)  NOT NULL DEFAULT '' COMMENT '失败原因（已去掉凭据）',
    `warnings`      json          NULL COMMENT '警告：线上检查没通过、推送失败、IndexNow 失败等',
    `deleted_at`    datetime      NULL COMMENT '软删除时间，NULL表示未删除；发布单不删除，保留字段只为符合建表规范',
    `create_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`     varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人（发起人）',
    `update_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`     varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_no` (`tenant_id`, `release_no`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_site_status` (`site_id`, `status`),
    KEY `idx_job_no` (`job_no`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='独立站发布单：一次把若干商品发布到一个站点';

CREATE TABLE `website_release_item` (
    `id`                 bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`          int(11)       NOT NULL DEFAULT 0 COMMENT '租户ID',
    `release_id`         bigint(20)    NOT NULL DEFAULT 0 COMMENT '发布单ID，关联website_release.id',
    `website_product_id` bigint(20)    NOT NULL DEFAULT 0 COMMENT '站点商品ID，关联website_product.id',
    `listing_task_id`    bigint(20)    NOT NULL DEFAULT 0 COMMENT '发品任务ID，关联listing_task.id',
    `prev_status`        tinyint(2)    NOT NULL DEFAULT 1 COMMENT '发起前发品任务的状态（1-待发布、2-已发布），失败时恢复',
    `snapshot`           json          NULL COMMENT '这次发布的站点数据快照（一个商品）',
    `live_urls`          json          NULL COMMENT '上线链接：{"en":"https://…","ru":"https://…"}',
    `check_result`       json          NULL COMMENT '线上检查结果：每个链接是否通过及原因',
    `create_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`          varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`          varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_release_id` (`release_id`),
    KEY `idx_website_product_id` (`website_product_id`),
    KEY `idx_listing_task_id` (`listing_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发布单商品：快照、上线链接与线上检查结果';

CREATE TABLE `website_release_step` (
    `id`          bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)       NOT NULL DEFAULT 0 COMMENT '租户ID',
    `release_id`  bigint(20)    NOT NULL DEFAULT 0 COMMENT '发布单ID，关联website_release.id',
    `step`        varchar(16)   NOT NULL DEFAULT '' COMMENT '步骤（pull、write、build、local_check、commit、deploy、online_check、push、indexnow）',
    `attempt`     int(11)       NOT NULL DEFAULT 1 COMMENT '第几次执行（从部署一步重试时加1）',
    `status`      tinyint(2)    NOT NULL DEFAULT 1 COMMENT '状态（1-执行中、2-成功、3-失败、4-警告）',
    `started_at`  datetime      NULL COMMENT '开始时间',
    `ended_at`    datetime      NULL COMMENT '结束时间',
    `message`     varchar(500)  NOT NULL DEFAULT '' COMMENT '结果说明（已去掉凭据）',
    `output`      text          NULL COMMENT '输出摘要：最后50行，已去掉凭据',
    `create_time` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_release_step` (`release_id`, `attempt`, `step`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发布单步骤：每一步的开始结束时间、结果与输出摘要';

-- ---------------------------------------------------------------- 发品任务
-- site_id：独立站任务为站点ID，其他平台为0；公司还没有站点时独立站任务也为0，建好站点后由一次性任务补上
ALTER TABLE `listing_task`
    ADD COLUMN `site_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '站点ID：独立站任务为website_site.id，其他平台为0' AFTER `platform`,
    MODIFY COLUMN `status` tinyint(2) NOT NULL DEFAULT 1 COMMENT '状态（1-待发布、2-已发布、3-已取消、4-发布中、5-已上线待确认）',
    ADD COLUMN `release_id` bigint(20) NOT NULL DEFAULT 0 COMMENT '最近一张独立站发布单ID，关联website_release.id' AFTER `published_at`,
    ADD COLUMN `site_outdated` tinyint(1) NOT NULL DEFAULT 0 COMMENT '独立站内容有更新（0-否、1-是）：站点数据与上次发布快照不同，定时任务和相关写入时重算' AFTER `release_id`,
    ADD COLUMN `confirmed_by` varchar(32) NOT NULL DEFAULT '' COMMENT '确认上线人' AFTER `site_outdated`,
    ADD COLUMN `confirmed_at` datetime NULL COMMENT '确认上线时间' AFTER `confirmed_by`,
    DROP INDEX `uk_tenant_product_platform`,
    ADD UNIQUE KEY `uk_tenant_product_platform_site` (`tenant_id`, `product_id`, `platform`, `site_id`),
    ADD KEY `idx_site_id` (`site_id`),
    ADD KEY `idx_release_id` (`release_id`);

-- ---------------------------------------------------------------- 菜单与权限
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT 100134, 100101, 'RS2100134', '站点管理', 2, 7, 'global', '', '', '', '/operations/websites', '', 1, '', 'sys', 'sys'
WHERE NOT EXISTS (SELECT 1 FROM `resource` WHERE `id` = 100134);
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT 110258, 100134, 'RS3110258', '修改站点', 3, 1, '', '', '', '', '', 'operations:website:edit', 1, '', 'sys', 'sys'
WHERE NOT EXISTS (SELECT 1 FROM `resource` WHERE `id` = 110258);
-- 发布到站点在发品任务上操作，挂在发品任务菜单下
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT 110259, 100117, 'RS3110259', '发布到站点', 3, 2, '', '', '', '', '', 'operations:website:publish', 1, '', 'sys', 'sys'
WHERE NOT EXISTS (SELECT 1 FROM `resource` WHERE `id` = 110259);
UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 100134)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`) AND NOT JSON_CONTAINS(`menu_ids`, '100134');
UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 110258)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`) AND NOT JSON_CONTAINS(`menu_ids`, '110258');
UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 110259)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`) AND NOT JSON_CONTAINS(`menu_ids`, '110259');
UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 100101)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`) AND NOT JSON_CONTAINS(`menu_ids`, '100101');
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` IN (100101, 100134, 110258, 110259)
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);
