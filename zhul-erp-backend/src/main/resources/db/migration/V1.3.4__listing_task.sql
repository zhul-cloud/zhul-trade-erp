-- 发品任务（openspec listing-tasks）：
-- 1. 发品任务表 listing_task：每家公司每个「商品 × 平台」一行，记录发布结果、参考素材、同步与替换；
-- 2. 菜单「运营管理 → 发品任务」与按钮权限「处理发品任务」，排在营销任务之后、素材库之前。
-- 已满足条件的商品由应用启动时的补生成（ListingTaskBackfill）生成任务，规则与平时生成一致。

CREATE TABLE `listing_task` (
    `id`               bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        int(11)      NOT NULL DEFAULT 0 COMMENT '租户ID',
    `product_id`       bigint(20)   NOT NULL DEFAULT 0 COMMENT '商品ID，关联product.id（共享商品库）',
    `platform`         varchar(16)  NOT NULL DEFAULT '' COMMENT '平台编码（alibaba-阿里国际站、mic-中国制造网、site-独立站、social-社媒）',
    `asset_key`        varchar(255) NOT NULL DEFAULT '' COMMENT '型号归并键：小写（品牌 + 空格 + 型号），用于关联素材库',
    `status`           tinyint(2)   NOT NULL DEFAULT 1 COMMENT '状态（1-待发布、2-已发布、3-已取消）',
    `cancel_reason`    varchar(32)  NOT NULL DEFAULT '' COMMENT '取消原因（平台已停用、标记不发布）',
    `ref_brand`        varchar(100) NOT NULL DEFAULT '' COMMENT '参考素材的品牌，为空表示用本型号素材',
    `ref_model`        varchar(150) NOT NULL DEFAULT '' COMMENT '参考素材的型号，为空表示用本型号素材',
    `ref_by`           varchar(32)  NOT NULL DEFAULT '' COMMENT '选择参考素材的人',
    `ref_time`         datetime     NULL COMMENT '选择参考素材的时间',
    `listing_url`      varchar(500) NOT NULL DEFAULT '' COMMENT '平台链接',
    `platform_item_id` varchar(64)  NOT NULL DEFAULT '' COMMENT '平台商品编号（阿里国际站、中国制造网）',
    `note`             varchar(300) NOT NULL DEFAULT '' COMMENT '备注',
    `published_by`     varchar(32)  NOT NULL DEFAULT '' COMMENT '发布人',
    `published_at`     datetime     NULL COMMENT '发布时间',
    `content_at`       datetime     NULL COMMENT '发布或上次同步时该平台营销内容的写入时间（独立站取英文、俄文较晚者）',
    `synced_by`        varchar(32)  NOT NULL DEFAULT '' COMMENT '上次同步人',
    `synced_at`        datetime     NULL COMMENT '上次同步时间',
    `replaced_by`      varchar(32)  NOT NULL DEFAULT '' COMMENT '上次替换素材的人',
    `replaced_at`      datetime     NULL COMMENT '上次替换素材的时间',
    `deleted_at`       datetime     NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`        varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`        varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_product_platform` (`tenant_id`, `product_id`, `platform`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_tenant_status` (`tenant_id`, `status`),
    KEY `idx_tenant_asset` (`tenant_id`, `asset_key`),
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发品任务：商品 × 平台的发布记录';

-- ---------------------------------------------------------------- 菜单与权限

-- 素材库顺延，发品任务排在营销任务之后
UPDATE `resource` SET `sort` = 5, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100116 AND `sort` = 4;

INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT 100117, 100101, 'RS2100117', '发品任务', 2, 4, 'upload', '', '', '', '/operations/listings', '', 1, '', 'sys', 'sys'
WHERE NOT EXISTS (SELECT 1 FROM `resource` WHERE `id` = 100117);

INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT 110213, 100117, 'RS3110213', '处理发品任务', 3, 1, '', '', '', '', '', 'operations:listing:edit', 1, '', 'sys', 'sys'
WHERE NOT EXISTS (SELECT 1 FROM `resource` WHERE `id` = 110213);

UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 100117)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`) AND NOT JSON_CONTAINS(`menu_ids`, '100117');
UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 110213)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`) AND NOT JSON_CONTAINS(`menu_ids`, '110213');
UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 100101)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`) AND NOT JSON_CONTAINS(`menu_ids`, '100101');

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` IN (100101, 100117, 110213)
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);
