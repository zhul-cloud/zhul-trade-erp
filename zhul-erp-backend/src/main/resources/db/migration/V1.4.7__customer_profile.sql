-- 客户档案（openspec/changes/customer-profile）：
-- 1. 客户画像五个模块的新字段、潜力评级、经营等级（RFM）；原 customer_grade（手工 A/B/C）停止使用，列保留；
-- 2. 客户联系人与决策人画像（存量主联系人迁入，主联系人仍同步回 customer.contact_*）；
-- 3. 画像字段来源；4. 国家资料（各公司自己维护）；5. 疑似重复「不是重复」的记录；
-- 6. 新字典、菜单「国家资料」与按钮权限「合并客户」、经营等级参数。

ALTER TABLE `customer`
    ADD COLUMN `linkedin`              varchar(300)  NOT NULL DEFAULT '' COMMENT 'LinkedIn 公司页' AFTER `website`,
    ADD COLUMN `social_url`            varchar(300)  NOT NULL DEFAULT '' COMMENT '其他社媒' AFTER `linkedin`,
    ADD COLUMN `founded_year`          smallint      NULL                COMMENT '成立年份',
    ADD COLUMN `company_size`          tinyint(2)    NOT NULL DEFAULT 0  COMMENT '公司规模（字典 customer_company_size；0-未填）',
    ADD COLUMN `potential_rating`      tinyint(2)    NOT NULL DEFAULT 0  COMMENT '潜力评级（1-5 星；0-未评）',
    ADD COLUMN `main_products`         varchar(300)  NOT NULL DEFAULT '' COMMENT '主营产品与服务',
    ADD COLUMN `cn_suppliers`          varchar(300)  NOT NULL DEFAULT '' COMMENT '现有中国供应商（名称或数量）',
    ADD COLUMN `est_annual_purchase`   decimal(18,2) NULL                COMMENT '年度采购量估算（美元，只是估算，不参与核算）',
    ADD COLUMN `est_purchase_currency` char(3)       NOT NULL DEFAULT 'USD' COMMENT '年度采购量估算的币种（固定 USD）',
    ADD COLUMN `quality_req`           tinyint(2)    NOT NULL DEFAULT 0  COMMENT '质量要求（字典 customer_quality_req；0-未填）',
    ADD COLUMN `price_sensitivity`     tinyint(2)    NOT NULL DEFAULT 0  COMMENT '价格敏感度（字典 customer_price_sensitivity；0-未填）',
    ADD COLUMN `customs_ability`       tinyint(2)    NOT NULL DEFAULT 0  COMMENT '清关能力（字典 customer_customs_ability；0-未填）',
    ADD COLUMN `current_brands`        json          NULL                COMMENT '现有使用品牌 [{brandId, name}]，brandId 可为空',
    ADD COLUMN `design_ability`        tinyint(2)    NOT NULL DEFAULT 0  COMMENT '设计能力（字典 customer_design_ability；0-未填）',
    ADD COLUMN `protocols`             json          NULL                COMMENT '常用通讯协议（字典 customer_protocol 码值数组）',
    ADD COLUMN `voltages`              json          NULL                COMMENT '电源电压标准（字典 customer_voltage 码值数组）',
    ADD COLUMN `certifications`        json          NULL                COMMENT '认证强制要求（字典 customer_certification 码值数组）',
    ADD COLUMN `repair_ability`        tinyint(2)    NOT NULL DEFAULT 0  COMMENT '售后维修能力（字典 customer_repair_ability；0-未填）',
    ADD COLUMN `biz_grade_calc`        tinyint(2)    NOT NULL DEFAULT 0  COMMENT '经营等级计算值（0-未成交、1-S、2-A、3-B、4-C）',
    ADD COLUMN `biz_grade`             tinyint(2)    NOT NULL DEFAULT 0  COMMENT '最终经营等级（计算值加人工调整；0-未成交、1-S、2-A、3-B、4-C）',
    ADD COLUMN `biz_grade_score`       decimal(4,1)  NULL                COMMENT 'RFM 综合分数',
    ADD COLUMN `rfm_json`              json          NULL                COMMENT 'RFM 明细（R/F/M 分数与原始值）',
    ADD COLUMN `rfm_calc_at`           datetime      NULL                COMMENT 'RFM 计算时间',
    ADD COLUMN `biz_grade_adjust`      tinyint(2)    NOT NULL DEFAULT 0  COMMENT '经营等级人工调整（-1 下调一级、0 不调整、1 上调一级）',
    ADD COLUMN `biz_grade_adjust_reason` varchar(200) NOT NULL DEFAULT '' COMMENT '经营等级调整原因',
    ADD COLUMN `biz_grade_adjust_by`   bigint        NOT NULL DEFAULT 0  COMMENT '调整人，关联 user_basic.id',
    ADD COLUMN `biz_grade_adjust_at`   datetime      NULL                COMMENT '调整时间',
    ADD COLUMN `profile_mask`          tinyint(2)    NOT NULL DEFAULT 0  COMMENT '已完成的档案模块（位：1-基础画像、2-商业能力、4-技术偏好、8-决策人、16-宏观背景；31 为已建档）',
    ADD KEY `idx_biz_grade` (`biz_grade`),
    ADD KEY `idx_profile_mask` (`profile_mask`),
    ADD KEY `idx_potential` (`potential_rating`),
    MODIFY COLUMN `customer_grade` tinyint(2) NOT NULL DEFAULT 0 COMMENT '已停用：原手工客户等级（0-未分级、1-A、2-B、3-C），由经营等级代替';

CREATE TABLE `customer_contact` (
    `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`      int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `customer_id`    bigint       NOT NULL DEFAULT 0  COMMENT '客户ID，关联customer.id',
    `is_primary`     tinyint(1)   NOT NULL DEFAULT 0  COMMENT '是否主联系人（0-否、1-是）；主联系人的联系方式同步到 customer.contact_*',
    `name`           varchar(100) NOT NULL DEFAULT '' COMMENT '姓名（英文）',
    `title`          varchar(50)  NOT NULL DEFAULT '' COMMENT '职位原文',
    `email`          varchar(100) NOT NULL DEFAULT '' COMMENT '邮箱',
    `phone`          varchar(32)  NOT NULL DEFAULT '' COMMENT '电话（与 customer.contact_phone 同宽，存量数据有超过 30 位的）',
    `whatsapp`       varchar(30)  NOT NULL DEFAULT '' COMMENT 'WhatsApp',
    `other_im`       varchar(100) NOT NULL DEFAULT '' COMMENT '其他联系方式',
    `email_key`      varchar(100) NOT NULL DEFAULT '' COMMENT '邮箱比较键（去空格、转小写）',
    `whatsapp_key`   varchar(30)  NOT NULL DEFAULT '' COMMENT 'WhatsApp比较键',
    `phone_key`      varchar(32)  NOT NULL DEFAULT '' COMMENT '电话比较键',
    `job_role`       tinyint(2)   NOT NULL DEFAULT 0  COMMENT '职位类别（字典 contact_job_role；0-未填）',
    `decision_role`  tinyint(2)   NOT NULL DEFAULT 0  COMMENT '决策权重（字典 contact_decision_role；0-未填）',
    `comm_style`     tinyint(2)   NOT NULL DEFAULT 0  COMMENT '沟通风格（字典 contact_comm_style；0-未填）',
    `disc`           tinyint(2)   NOT NULL DEFAULT 0  COMMENT '性格 DISC（字典 contact_disc；0-未填）',
    `china_attitude` tinyint(2)   NOT NULL DEFAULT 0  COMMENT '对华态度（字典 contact_china_attitude；0-未填）',
    `active_hours`   varchar(50)  NOT NULL DEFAULT '' COMMENT '活跃时间（北京时间段）',
    `likes`          varchar(300) NOT NULL DEFAULT '' COMMENT '禁忌与喜好',
    `deleted_at`     datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`      varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`      varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_email_key` (`email_key`),
    KEY `idx_whatsapp_key` (`whatsapp_key`),
    KEY `idx_phone_key` (`phone_key`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户联系人与决策人画像';

-- 存量主联系人迁入（姓名或任一联系方式不为空的）
INSERT INTO `customer_contact` (`tenant_id`, `customer_id`, `is_primary`, `name`, `title`, `email`, `phone`, `whatsapp`, `other_im`,
                                `email_key`, `whatsapp_key`, `phone_key`, `create_time`, `create_by`, `update_time`, `update_by`)
SELECT c.`tenant_id`, c.`id`, 1, c.`contact_name`, c.`contact_title`, c.`contact_email`, c.`contact_phone`, c.`whatsapp`, c.`other_im`,
       c.`email_key`, c.`whatsapp_key`, c.`phone_key`, c.`create_time`, c.`create_by`, c.`update_time`, c.`update_by`
FROM `customer` c
WHERE c.`deleted_at` IS NULL
  AND (c.`contact_name` <> '' OR c.`contact_email` <> '' OR c.`contact_phone` <> '' OR c.`whatsapp` <> '' OR c.`other_im` <> '');

CREATE TABLE `customer_field_source` (
    `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)     NOT NULL DEFAULT 0  COMMENT '租户ID',
    `customer_id` bigint      NOT NULL DEFAULT 0  COMMENT '客户ID，关联customer.id',
    `field_key`   varchar(64) NOT NULL DEFAULT '' COMMENT '画像字段键，联系人字段为 contact.{联系人ID}.{字段}',
    `source`      tinyint(2)  NOT NULL DEFAULT 1  COMMENT '来源（1-人工、2-背调报告、3-迁移）',
    `report_id`   bigint      NOT NULL DEFAULT 0  COMMENT '来源背调报告ID，0 为无',
    `operator_id` bigint      NOT NULL DEFAULT 0  COMMENT '最后更新人，关联user_basic.id',
    `asked_by`    bigint      NOT NULL DEFAULT 0  COMMENT '已问客户、等回复：提问人，0 为没有在等',
    `asked_at`    datetime    NULL                COMMENT '已问客户、等回复：提问时间',
    `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_customer_field` (`customer_id`, `field_key`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_report_id` (`report_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户画像字段来源';

CREATE TABLE `country_profile` (
    `id`             int(11)      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`      int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `country`        varchar(64)  NOT NULL DEFAULT '' COMMENT '国家/地区（系统国家清单英文名）',
    `voltages`       json         NULL                COMMENT '工业电压与频率（字典 customer_voltage 码值数组）',
    `plug`           varchar(100) NOT NULL DEFAULT '' COMMENT '插头类型',
    `holidays`       varchar(500) NOT NULL DEFAULT '' COMMENT '重大节假日',
    `fx_control`     tinyint(2)   NOT NULL DEFAULT 0  COMMENT '外汇与支付管制（0-未填、1-无、2-一般、3-严格）',
    `fx_note`        varchar(500) NOT NULL DEFAULT '' COMMENT '管制说明',
    `tariff_note`    varchar(500) NOT NULL DEFAULT '' COMMENT '进口关税备注',
    `certifications` json         NULL                COMMENT '常见认证要求（字典 customer_certification 码值数组）',
    `deleted_at`     datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`      varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`      varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_country` (`tenant_id`, `country`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB AUTO_INCREMENT=1000 DEFAULT CHARSET=utf8mb4 COMMENT='国家资料（各公司自己维护）';

CREATE TABLE `customer_dup_dismiss` (
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `group_key`   varchar(200) NOT NULL DEFAULT '' COMMENT '疑似重复组的键：成组原因 + 排序后的客户ID；组内成员变化后键不同，组会重新出现',
    `deleted_at`  datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_group` (`tenant_id`, `group_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='疑似重复客户：标为「不是重复」的组';

-- ---------------------------------------------------------------- 字典
INSERT INTO `dict_type` (`tenant_id`, `dict_type`, `dict_name`, `is_builtin`, `status`, `remark`, `create_by`, `update_by`)
SELECT 0, v.t, v.n, 1, 1, v.r, 'sys', 'sys'
FROM (
              SELECT 'customer_company_size' AS t, '客户公司规模' AS n, '客户画像·基础' AS r
    UNION ALL SELECT 'customer_quality_req', '客户质量要求', '客户画像·商业'
    UNION ALL SELECT 'customer_price_sensitivity', '客户价格敏感度', '客户画像·商业'
    UNION ALL SELECT 'customer_customs_ability', '客户清关能力', '客户画像·商业'
    UNION ALL SELECT 'customer_design_ability', '客户设计能力', '客户画像·技术'
    UNION ALL SELECT 'customer_protocol', '常用通讯协议', '客户画像·技术，可多选'
    UNION ALL SELECT 'customer_voltage', '电源电压标准', '客户画像·技术与国家资料，可多选'
    UNION ALL SELECT 'customer_certification', '认证强制要求', '客户画像·技术与国家资料，可多选'
    UNION ALL SELECT 'customer_repair_ability', '售后维修能力', '客户画像·技术'
    UNION ALL SELECT 'contact_job_role', '联系人职位类别', '决策人画像'
    UNION ALL SELECT 'contact_decision_role', '联系人决策权重', '决策人画像'
    UNION ALL SELECT 'contact_comm_style', '联系人沟通风格', '决策人画像'
    UNION ALL SELECT 'contact_disc', '联系人性格（DISC）', '决策人画像'
    UNION ALL SELECT 'contact_china_attitude', '联系人对华态度', '决策人画像'
) v
WHERE NOT EXISTS (SELECT 1 FROM `dict_type` d WHERE d.`dict_type` = v.t AND d.`tenant_id` = 0);

INSERT INTO `dict_item` (`tenant_id`, `dict_type_id`, `dict_type`, `item_code`, `item_name`, `item_name_en`, `item_value`, `sort_order`, `is_default`, `status`, `remark`, `create_by`, `update_by`)
SELECT 0, t.`id`, t.`dict_type`, v.code, v.name, '', v.code, v.code, 0, 1, '', 'sys', 'sys'
FROM `dict_type` t
JOIN (
              SELECT 'customer_company_size' AS dt, '1' AS code, '<10 人' AS name
    UNION ALL SELECT 'customer_company_size', '2', '10–50 人'
    UNION ALL SELECT 'customer_company_size', '3', '50 人以上'
    UNION ALL SELECT 'customer_quality_req', '1', '极高（大牌平替）'
    UNION ALL SELECT 'customer_quality_req', '2', '中等（性价比）'
    UNION ALL SELECT 'customer_quality_req', '3', '低（能用就行）'
    UNION ALL SELECT 'customer_price_sensitivity', '1', '极高（比价）'
    UNION ALL SELECT 'customer_price_sensitivity', '2', '中等'
    UNION ALL SELECT 'customer_price_sensitivity', '3', '低（缺货急需）'
    UNION ALL SELECT 'customer_customs_ability', '1', '有代理'
    UNION ALL SELECT 'customer_customs_ability', '2', '需双清包税（DDP）'
    UNION ALL SELECT 'customer_customs_ability', '3', '无经验'
    UNION ALL SELECT 'customer_design_ability', '1', '有工程师团队'
    UNION ALL SELECT 'customer_design_ability', '2', '无（需要技术支持）'
    UNION ALL SELECT 'customer_protocol', '1', 'Modbus'
    UNION ALL SELECT 'customer_protocol', '2', 'Profinet'
    UNION ALL SELECT 'customer_protocol', '3', 'EtherCAT'
    UNION ALL SELECT 'customer_protocol', '4', 'CANopen'
    UNION ALL SELECT 'customer_protocol', '5', 'EtherNet/IP'
    UNION ALL SELECT 'customer_protocol', '6', 'Profibus'
    UNION ALL SELECT 'customer_protocol', '9', '其他'
    UNION ALL SELECT 'customer_voltage', '1', '110V'
    UNION ALL SELECT 'customer_voltage', '2', '220V'
    UNION ALL SELECT 'customer_voltage', '3', '230V'
    UNION ALL SELECT 'customer_voltage', '4', '380V'
    UNION ALL SELECT 'customer_voltage', '5', '400V'
    UNION ALL SELECT 'customer_voltage', '6', '480V'
    UNION ALL SELECT 'customer_voltage', '7', '50Hz'
    UNION ALL SELECT 'customer_voltage', '8', '60Hz'
    UNION ALL SELECT 'customer_certification', '1', 'CE'
    UNION ALL SELECT 'customer_certification', '2', 'UL'
    UNION ALL SELECT 'customer_certification', '3', 'RoHS'
    UNION ALL SELECT 'customer_certification', '4', 'FCC'
    UNION ALL SELECT 'customer_certification', '9', '其他'
    UNION ALL SELECT 'customer_repair_ability', '1', '自己能修'
    UNION ALL SELECT 'customer_repair_ability', '2', '坏了直接换'
    UNION ALL SELECT 'customer_repair_ability', '3', '寄回中国维修'
    UNION ALL SELECT 'contact_job_role', '1', '采购'
    UNION ALL SELECT 'contact_job_role', '2', '工程师'
    UNION ALL SELECT 'contact_job_role', '3', '老板'
    UNION ALL SELECT 'contact_job_role', '4', '销售'
    UNION ALL SELECT 'contact_job_role', '9', '其他'
    UNION ALL SELECT 'contact_decision_role', '1', '拍板人'
    UNION ALL SELECT 'contact_decision_role', '2', '建议者'
    UNION ALL SELECT 'contact_decision_role', '3', '执行者'
    UNION ALL SELECT 'contact_comm_style', '1', '简洁高效'
    UNION ALL SELECT 'contact_comm_style', '2', '闲聊型'
    UNION ALL SELECT 'contact_comm_style', '3', '严谨抠细节'
    UNION ALL SELECT 'contact_disc', '1', 'D 掌控型'
    UNION ALL SELECT 'contact_disc', '2', 'I 社交型'
    UNION ALL SELECT 'contact_disc', '3', 'S 稳健型'
    UNION ALL SELECT 'contact_disc', '4', 'C 分析型'
    UNION ALL SELECT 'contact_china_attitude', '1', '友好（来过中国）'
    UNION ALL SELECT 'contact_china_attitude', '2', '中立'
    UNION ALL SELECT 'contact_china_attitude', '3', '偏见或排斥'
) v ON v.dt = t.`dict_type`
WHERE t.`tenant_id` = 0
  AND NOT EXISTS (SELECT 1 FROM `dict_item` i WHERE i.`dict_type` = v.dt AND i.`item_code` = v.code AND i.`tenant_id` = 0);

-- ---------------------------------------------------------------- 菜单与权限
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
              SELECT 100122 AS id, 100084 AS pid, 'RS2100122' AS code, '国家资料' AS name, 2 AS type, 7 AS sort, 'globe' AS li, '' AS lsi, '' AS di, '' AS dsi,
                     '/system/country-profiles' AS path, '' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
    UNION ALL SELECT 110224, 100061, 'RS3110224', '合并客户', 3, 7, '', '', '', '', '', 'partner:customer:merge', 1, '', 'sys', 'sys'
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.id);

UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 100122)
WHERE JSON_CONTAINS(`menu_ids`, '100075') AND NOT JSON_CONTAINS(`menu_ids`, '100122');
UPDATE `tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 110224)
WHERE JSON_CONTAINS(`menu_ids`, '110145') AND NOT JSON_CONTAINS(`menu_ids`, '110224');

-- 有汇率设置菜单的角色得到国家资料；有转移客户权限的角色得到合并客户
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, 100122, 'RS2100122' FROM `role_resource` x
WHERE x.`resource_id` = 100075
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = 100122);
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, 110224, 'RS3110224' FROM `role_resource` x
WHERE x.`resource_id` = 110145
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = 110224);

-- ---------------------------------------------------------------- 经营等级参数（平台模板，租户可覆盖）
INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_type`, `is_builtin`, `config_group`, `remark`, `create_by`, `update_by`)
SELECT 0, k.config_key, k.config_name, k.config_value, 'STRING', 1, 'customer', k.remark, 'sys', 'sys'
FROM (
              SELECT 'customer.grade.recency-days' AS config_key, '经营等级 R 分界（天）' AS config_name, '30,90,180,365' AS config_value, '距最近订单天数，依次为 5/4/3/2 分的上限，超过最后一个为 1 分' AS remark
    UNION ALL SELECT 'customer.grade.frequency-orders', '经营等级 F 分界（单）', '13,6,3,1', '近 12 个月订单数，依次为 5/4/3/2 分的下限，0 单为 1 分'
    UNION ALL SELECT 'customer.grade.monetary-usd', '经营等级 M 分界（美元）', '9000,4700,4000,1800', '近 12 个月销售额（美元），依次为 5/4/3/2 分的下限'
    UNION ALL SELECT 'customer.grade.weights', '经营等级权重 R,F,M', '0.3,0.4,0.3', '综合分数 = R×权重 + F×权重 + M×权重'
    UNION ALL SELECT 'customer.grade.cutoffs', '经营等级分界 S,A,B', '4.5,3.5,2.5', '综合分数不低于分界即为该等级，其余为 C'
    UNION ALL SELECT 'customer.grade.follow-days', '建议跟进间隔 S,A,B,C（天）', '7,14,30,60', '客户详情显示的建议跟进频率'
) k
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` c WHERE c.`tenant_id` = 0 AND c.`config_key` = k.config_key);

-- 回退：
-- DELETE FROM `sys_config` WHERE `tenant_id` = 0 AND `config_key` LIKE 'customer.grade.%';
-- DELETE FROM `role_resource` WHERE `resource_id` IN (100122, 110224);
-- DELETE FROM `resource` WHERE `id` IN (100122, 110224);
-- （tenant_package.menu_ids 中多出的 ID 不影响旧版本，可保留）
-- DELETE FROM `dict_item` WHERE `tenant_id` = 0 AND `dict_type` IN ('customer_company_size','customer_quality_req','customer_price_sensitivity','customer_customs_ability','customer_design_ability','customer_protocol','customer_voltage','customer_certification','customer_repair_ability','contact_job_role','contact_decision_role','contact_comm_style','contact_disc','contact_china_attitude');
-- DELETE FROM `dict_type` WHERE `tenant_id` = 0 AND `dict_type` IN (同上);
-- DROP TABLE `customer_dup_dismiss`;
-- DROP TABLE `country_profile`;
-- DROP TABLE `customer_field_source`;
-- DROP TABLE `customer_contact`;   -- 主联系人仍在 customer.contact_* 里，不会丢
-- ALTER TABLE `customer` DROP INDEX `idx_biz_grade`, DROP INDEX `idx_potential`,
--     DROP COLUMN `linkedin`, DROP COLUMN `social_url`, DROP COLUMN `founded_year`, DROP COLUMN `company_size`, DROP COLUMN `potential_rating`,
--     DROP COLUMN `main_products`, DROP COLUMN `cn_suppliers`, DROP COLUMN `est_annual_purchase`, DROP COLUMN `est_purchase_currency`,
--     DROP COLUMN `quality_req`, DROP COLUMN `price_sensitivity`, DROP COLUMN `customs_ability`, DROP COLUMN `current_brands`,
--     DROP COLUMN `design_ability`, DROP COLUMN `protocols`, DROP COLUMN `voltages`, DROP COLUMN `certifications`, DROP COLUMN `repair_ability`,
--     DROP COLUMN `biz_grade_calc`, DROP COLUMN `biz_grade`, DROP COLUMN `biz_grade_score`, DROP COLUMN `rfm_json`, DROP COLUMN `rfm_calc_at`,
--     DROP COLUMN `biz_grade_adjust`, DROP COLUMN `biz_grade_adjust_reason`, DROP COLUMN `biz_grade_adjust_by`, DROP COLUMN `biz_grade_adjust_at`,
--     DROP INDEX `idx_profile_mask`, DROP COLUMN `profile_mask`;
