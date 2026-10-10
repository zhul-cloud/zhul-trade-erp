-- 商品候选池与询盘自动建档（openspec add-product-candidate-pool）

-- ---------------------------------------------------------------- 候选池（平台级，tenant_id 固定为 0）
CREATE TABLE IF NOT EXISTS `product_candidate` (
    `id`              bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`       int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID（候选池为平台级，固定 0）',
    `brand_id`        bigint        NULL                COMMENT '识别到的品牌ID，未识别为空',
    `brand_text`      varchar(64)   NOT NULL DEFAULT '' COMMENT '品牌原文',
    `brand_key`       varchar(80)   NOT NULL DEFAULT '' COMMENT '品牌匹配键：#品牌ID，未识别为小写品牌名',
    `mpn_raw`         varchar(128)  NOT NULL DEFAULT '' COMMENT '原始型号',
    `mpn_normalized`  varchar(128)  NOT NULL DEFAULT '' COMMENT '归一化型号（与商品主数据同一规则）',
    `category_id`     bigint        NULL                COMMENT '建议品类ID，匹配不到为空',
    `category_text`   varchar(64)   NOT NULL DEFAULT '' COMMENT '询盘里的品类原文',
    `product_name`    varchar(255)  NOT NULL DEFAULT '' COMMENT '建议商品名称',
    `description`     varchar(500)  NOT NULL DEFAULT '' COMMENT '中文描述（取自询盘）',
    `description_en`  varchar(500)  NOT NULL DEFAULT '' COMMENT '英文描述（取自询盘）',
    `status`          tinyint(2)    NOT NULL DEFAULT 1  COMMENT '状态（1-待审核、2-已建档、3-已并入、4-已驳回）',
    `level`           tinyint(2)    NOT NULL DEFAULT 1  COMMENT '可信度（1-询盘出现、2-采购问到有货、3-已成交）',
    `source_count`    int           NOT NULL DEFAULT 0  COMMENT '来源条数',
    `first_seen_at`   datetime      NULL                COMMENT '首次出现时间',
    `last_seen_at`    datetime      NULL                COMMENT '最近出现时间',
    `product_id`      bigint        NULL                COMMENT '建档或并入的商品ID',
    `reject_reason`   tinyint(2)    NOT NULL DEFAULT 0  COMMENT '驳回原因（0-无、1-不是型号、2-型号错误、3-其他）',
    `reject_note`     varchar(200)  NOT NULL DEFAULT '' COMMENT '驳回说明',
    `reviewed_by`     bigint        NULL                COMMENT '审核人用户ID',
    `reviewed_at`     datetime      NULL                COMMENT '审核时间',
    `deleted_at`      datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`       varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`       varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_candidate_brand_mpn` (`brand_key`, `mpn_normalized`),
    KEY `idx_candidate_tenant` (`tenant_id`),
    KEY `idx_candidate_status` (`status`, `level`),
    KEY `idx_candidate_brand` (`brand_id`),
    KEY `idx_candidate_product` (`product_id`),
    KEY `idx_candidate_deleted` (`deleted_at`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000 DEFAULT CHARSET = utf8mb4 COMMENT = '商品候选池';

CREATE TABLE IF NOT EXISTS `product_candidate_source` (
    `id`                   bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`            int(11)     NOT NULL DEFAULT 0  COMMENT '来源所属租户ID',
    `candidate_id`         bigint      NOT NULL            COMMENT '候选ID',
    `source_type`          tinyint(2)  NOT NULL DEFAULT 1  COMMENT '来源类型（1-询盘、2-采购回填真实型号、3-采购回价有货、4-成交、5-手动、6-导入）',
    `customer_inquiry_id`  bigint      NULL                COMMENT '客户询盘ID',
    `inquiry_item_id`      bigint      NULL                COMMENT '询盘型号ID',
    `so_id`                bigint      NULL                COMMENT '销售订单ID（成交来源）',
    `deleted_at`           datetime    NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`          datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`            varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`          datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`            varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_cand_source_tenant` (`tenant_id`),
    KEY `idx_cand_source_candidate` (`candidate_id`),
    KEY `idx_cand_source_item` (`inquiry_item_id`),
    KEY `idx_cand_source_so` (`so_id`),
    KEY `idx_cand_source_deleted` (`deleted_at`)
) ENGINE = InnoDB AUTO_INCREMENT = 10000 DEFAULT CHARSET = utf8mb4 COMMENT = '商品候选来源';

-- ---------------------------------------------------------------- 询盘型号：真实型号与建档状态
ALTER TABLE `inquiry_item`
    ADD COLUMN `actual_model`     varchar(128) NOT NULL DEFAULT '' COMMENT '采购回填的真实型号（询盘原文不是型号或写错时）' AFTER `model_key`,
    ADD COLUMN `actual_model_key` varchar(128) NOT NULL DEFAULT '' COMMENT '真实型号的归一化键' AFTER `actual_model`,
    ADD COLUMN `archive_status`   tinyint(2)   NOT NULL DEFAULT 0  COMMENT '建档状态（0-未处理、1-已建档、2-候选中、3-待回填真实型号）' AFTER `actual_model_key`,
    ADD KEY `idx_inquiry_item_product` (`product_id`);

-- ---------------------------------------------------------------- 菜单：商品资料 → 商品候选；按钮：商品候选审核
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
              SELECT 100099 AS id, 100004 AS pid, 'RS2100099' AS code, '商品候选' AS name, 2 AS type, 2 AS sort, 'audit' AS li, '' AS lsi, '' AS di, '' AS dsi,
                     '/product/candidates' AS path, '' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
    UNION ALL SELECT 110201, 100099, 'RS3110201', '商品候选审核', 3, 1, '', '', '', '', '', 'product:candidate:review', 1, '', 'sys', 'sys'
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.id);

-- 商品资料：商品、商品候选、品牌、品类、系列
UPDATE `resource` SET `sort` = 3, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100031;
UPDATE `resource` SET `sort` = 4, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100032;
UPDATE `resource` SET `sort` = 5, `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100033;

UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(`menu_ids`, '$', 100099), '$', 110201)
WHERE `name` IN ('标准版', '旗舰版') AND NOT JSON_CONTAINS(`menu_ids`, '100099');

-- 能看「商品」的角色可以看「商品候选」；审核按钮只授给内置管理员
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, s.`id`, s.`code`
FROM `role_resource` x
JOIN `resource` s ON s.`id` = 100099
WHERE x.`resource_id` = 100034
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = s.`id`);

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT r.`code`, s.`id`, s.`code`
FROM `role` r
JOIN `resource` s ON s.`id` IN (100099, 110201)
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = s.`id`);
