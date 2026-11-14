-- V1.5.35：供应链管理第二期——供应商开发（OpenSpec supply-chain-phase2-supplier-development）
-- 1. 两张新表：主动拓展范围、候选供应商
-- 2. 专项加「目标家数」（带值即为开发任务）；处理记录加对象类型与涉及的候选
-- 3. 按钮权限「维护供应商开发」，授给内置租户管理员
-- 作战卡 SC-M5、SC-V1、SC-V2 已在 V1.5.34 导入，这里不再导入。
-- 回退见 sql/build/rollback/V1.5.35__supply_chain_phase2_rollback.sql

CREATE TABLE `sc_expansion_scope` (
    `id`           bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`    int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `brand_id`     bigint        NOT NULL DEFAULT 0  COMMENT '品牌ID，关联product_brand.id',
    `category_id`  bigint        NOT NULL DEFAULT 0  COMMENT '二级品类ID，关联product_category.id',
    `heat`         tinyint(2)    NOT NULL DEFAULT 2  COMMENT '热度（1-低、2-中、3-高）',
    `reason`       varchar(200)  NOT NULL DEFAULT '' COMMENT '加入理由',
    `owner_id`     bigint        NOT NULL DEFAULT 0  COMMENT '负责人，关联user_basic.id，0 为未指定',
    `active_scope` varchar(48)   NULL                COMMENT '有效时为「品牌ID:品类ID」，移出后置 NULL，保证同一范围只有一条有效记录',
    `remove_reason` varchar(200) NOT NULL DEFAULT '' COMMENT '移出原因',
    `deleted_at`   datetime      NULL                COMMENT '软删除（移出）时间，NULL表示有效',
    `create_time`  datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`    varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`  datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`    varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_active_scope` (`tenant_id`, `active_scope`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_brand_category` (`brand_id`, `category_id`),
    KEY `idx_owner_id` (`owner_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应链主动拓展范围（公司想进入、还没有询盘的品牌 × 二级品类）';

CREATE TABLE `sc_dev_candidate` (
    `id`               bigint        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        int(11)       NOT NULL DEFAULT 0  COMMENT '租户ID',
    `project_id`       bigint        NOT NULL DEFAULT 0  COMMENT '开发任务（专项）ID，关联sc_gap_project.id',
    `supplier_id`      bigint        NOT NULL DEFAULT 0  COMMENT '供应商ID，关联supplier.id，0 为还没关联供应商库',
    `link_key`         varchar(48)   NULL                COMMENT '关联供应商库后为「专项ID:供应商ID」，保证同一开发任务下同一家供应商只登记一次',
    `name`             varchar(100)  NOT NULL DEFAULT '' COMMENT '名称（关联供应商库后以供应商库为准，这里保留登记时的名称）',
    `contact_name`     varchar(50)   NOT NULL DEFAULT '' COMMENT '联系人',
    `contact_info`     varchar(100)  NOT NULL DEFAULT '' COMMENT '联系方式',
    `source`           tinyint(2)    NOT NULL DEFAULT 6  COMMENT '来源（1-搜索、2-展会、3-同行介绍、4-平台、5-供应商库、6-其他）',
    `stage`            tinyint(2)    NOT NULL DEFAULT 1  COMMENT '阶段（1-待联系、2-已联系、3-已报价、4-合格、5-已放弃）；合格只由系统按证据标上',
    `abandon_reason`   varchar(200)  NOT NULL DEFAULT '' COMMENT '放弃原因',
    `note`             varchar(300)  NOT NULL DEFAULT '' COMMENT '备注',
    `evidence_type`    tinyint(2)    NOT NULL DEFAULT 0  COMMENT '合格依据类型（0-无、1-询价记录、2-采购订单行）',
    `evidence_id`      bigint        NOT NULL DEFAULT 0  COMMENT '合格依据ID（询价记录ID或采购订单行ID）',
    `evidence_date`    date          NULL                COMMENT '合格依据日期（询价日期或下单日期）',
    `quoted_at`        date          NULL                COMMENT '进入「已报价」的日期，用于提醒关联供应商库',
    `next_action`      varchar(200)  NOT NULL DEFAULT '' COMMENT '下一步行动',
    `next_action_date` date          NULL                COMMENT '下一步日期',
    `owner_id`         bigint        NOT NULL DEFAULT 0  COMMENT '负责人，关联user_basic.id',
    `last_action`      varchar(200)  NOT NULL DEFAULT '' COMMENT '最近一次处理内容（显示用）',
    `last_action_at`   datetime      NULL                COMMENT '最近一次处理时间',
    `last_action_by`   bigint        NOT NULL DEFAULT 0  COMMENT '最近一次处理人，关联user_basic.id，0 为系统',
    `deleted_at`       datetime      NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`        varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_link_key` (`tenant_id`, `link_key`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_project_id` (`project_id`),
    KEY `idx_supplier_id` (`supplier_id`),
    KEY `idx_stage` (`stage`),
    KEY `idx_owner_next` (`owner_id`, `next_action_date`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应链开发任务的候选供应商';

ALTER TABLE `sc_gap_project`
    ADD COLUMN `supplier_target` int NULL COMMENT '开发供应商目标家数（1–10）；为空表示只管型号，不是开发任务' AFTER `due_date`;

ALTER TABLE `sc_followup_log`
    ADD COLUMN `object_type` tinyint(2) NOT NULL DEFAULT 1 COMMENT '对象类型（1-型号、2-候选供应商）' AFTER `source_doc_no`,
    ADD COLUMN `candidate_ids` json NULL COMMENT '涉及的候选供应商ID（数组），对象类型为候选时有值' AFTER `demand_ids`,
    ADD KEY `idx_object_type` (`object_type`);

-- ---------------------------------------------------------------- 按钮权限
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT 110242, 110236, 'RS3110242', '维护供应商开发', 3, 4, '', '', '', '', '', 'supply:dev:edit', 1, '', 'sys', 'sys'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = 110242);

UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 110242)
WHERE `menu_ids` IS NOT NULL AND JSON_VALID(`menu_ids`) AND JSON_CONTAINS(`menu_ids`, '110236') AND NOT JSON_CONTAINS(`menu_ids`, '110242');

INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT r.`code`, 110242, 'RS3110242'
FROM `role` r
WHERE r.`code` = 'ROLE_ADMIN' AND r.`is_built_in` = 1
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = r.`code` AND y.`resource_id` = 110242);
