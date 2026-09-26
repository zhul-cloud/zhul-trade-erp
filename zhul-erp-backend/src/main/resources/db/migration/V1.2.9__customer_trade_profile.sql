-- V1.2.9：外贸客户档案（编码、角色、负责业务员、注册地址与税务、主联系人、默认交易条件）与客户单证主体，
-- 见 openspec/changes/enrich-customer-trade-profile/。
ALTER TABLE `customer`
    ADD COLUMN `customer_code`    varchar(20)   NOT NULL DEFAULT '' COMMENT '客户编码（字母数字，租户内未删除记录唯一；自动生成格式 CUS+5位补零主键）' AFTER `tenant_id`,
    MODIFY COLUMN `name`          varchar(200)  NOT NULL DEFAULT '' COMMENT '客户名称（英文法定全称，单据使用）',
    ADD COLUMN `name_key`         varchar(200)  NOT NULL DEFAULT '' COMMENT '规范化名称（小写、去标点空白与公司后缀），与国家一起查重' AFTER `name`,
    ADD COLUMN `name_cn`          varchar(100)  NOT NULL DEFAULT '' COMMENT '中文名称（内部使用）' AFTER `name_key`,
    ADD COLUMN `short_name`       varchar(50)   NOT NULL DEFAULT '' COMMENT '客户简称' AFTER `name_cn`,
    ADD COLUMN `customer_role`    tinyint(2)    NOT NULL DEFAULT 0  COMMENT '客户角色（0-未设置、1-终端用户、2-系统集成商、3-经销商、4-贸易商、5-OEM设备厂、6-维修服务商、7-其他）' AFTER `short_name`,
    ADD COLUMN `industry`         tinyint(2)    NOT NULL DEFAULT 0  COMMENT '应用行业（0-未设置、1-汽车制造、2-电子半导体、3-食品饮料、4-包装印刷、5-纺织、6-冶金钢铁、7-石油化工、8-水处理、9-电力能源、10-矿山、11-港口物流、12-楼宇暖通、13-其他）' AFTER `customer_role`,
    ADD COLUMN `website`          varchar(200)  NOT NULL DEFAULT '' COMMENT '官网' AFTER `industry`,
    ADD COLUMN `customer_grade`   tinyint(2)    NOT NULL DEFAULT 0  COMMENT '客户等级（0-未分级、1-A、2-B、3-C），当前手工标记' AFTER `website`,
    ADD COLUMN `source_channel`   tinyint(2)    NOT NULL DEFAULT 0  COMMENT '客户来源（0-未设置、1-阿里巴巴国际站、2-中国制造网、3-独立站、4-展会、5-社交媒体、6-老客户转介绍、7-主动开发、8-其他）' AFTER `customer_grade`,
    ADD COLUMN `owner_id`         bigint(20)    NOT NULL DEFAULT 0  COMMENT '负责业务员ID，关联user_basic.id，0表示未分配' AFTER `source_channel`,
    ADD COLUMN `external_ref`     varchar(50)   NOT NULL DEFAULT '' COMMENT '小满客户编号' AFTER `owner_id`,
    MODIFY COLUMN `country`       varchar(64)   NOT NULL DEFAULT '' COMMENT '国家/地区（系统国家清单英文名）',
    ADD COLUMN `state`            varchar(100)  NOT NULL DEFAULT '' COMMENT '州/省' AFTER `country`,
    ADD COLUMN `city`             varchar(100)  NOT NULL DEFAULT '' COMMENT '城市' AFTER `state`,
    ADD COLUMN `postcode`         varchar(20)   NOT NULL DEFAULT '' COMMENT '邮编' AFTER `city`,
    ADD COLUMN `address`          varchar(300)  NOT NULL DEFAULT '' COMMENT '详细地址（英文）' AFTER `postcode`,
    ADD COLUMN `tax_id`           varchar(50)   NOT NULL DEFAULT '' COMMENT '税号（VAT/EIN/GST/CNPJ等）' AFTER `address`,
    ADD COLUMN `timezone`         varchar(64)   NOT NULL DEFAULT '' COMMENT '时区（IANA名，如Europe/Berlin）' AFTER `tax_id`,
    MODIFY COLUMN `contact_name`  varchar(100)  NOT NULL DEFAULT '' COMMENT '主联系人姓名',
    ADD COLUMN `contact_title`    varchar(50)   NOT NULL DEFAULT '' COMMENT '主联系人职位' AFTER `contact_name`,
    ADD COLUMN `whatsapp`         varchar(30)   NOT NULL DEFAULT '' COMMENT '主联系人WhatsApp' AFTER `contact_email`,
    ADD COLUMN `other_im`         varchar(100)  NOT NULL DEFAULT '' COMMENT '其他联系方式（微信/Skype/LinkedIn等）' AFTER `whatsapp`,
    ADD COLUMN `currency`         char(3)       NOT NULL DEFAULT 'USD' COMMENT '默认币种（ISO 4217：USD/EUR/GBP/JPY/CNY）' AFTER `other_im`,
    ADD COLUMN `incoterm`         varchar(3)    NOT NULL DEFAULT '' COMMENT '默认贸易术语（Incoterms 2020：EXW/FCA/FOB/CFR/CIF/CPT/CIP/DAP/DPU/DDP，空为未设置）' AFTER `currency`,
    ADD COLUMN `incoterm_place`   varchar(100)  NOT NULL DEFAULT '' COMMENT '贸易术语地点（装运港或目的地）' AFTER `incoterm`,
    ADD COLUMN `payment_method`   tinyint(2)    NOT NULL DEFAULT 0  COMMENT '默认付款方式（0-未设置、1-T/T全额预付、2-T/T定金+发货前付尾款、3-T/T定金+见提单副本付尾款、4-L/C即期、5-L/C远期、6-D/P、7-D/A、8-O/A赊销、9-其他）' AFTER `incoterm_place`,
    ADD COLUMN `deposit_ratio`    tinyint(3)    NULL                COMMENT '定金比例（%，0-100，付款方式含定金时有值）' AFTER `payment_method`,
    ADD COLUMN `payment_days`     smallint(4)   NULL                COMMENT '账期（天，0-365，L/C远期、D/A、O/A时有值）' AFTER `deposit_ratio`,
    ADD COLUMN `credit_limit`     DECIMAL(18,2) NULL                COMMENT '信用额度（原币金额，限额配置而非交易金额，与credit_currency成对；当前只记录不管控）' AFTER `payment_days`,
    ADD COLUMN `credit_currency`  char(3)       NOT NULL DEFAULT '' COMMENT '信用额度币种（ISO 4217）' AFTER `credit_limit`,
    ADD COLUMN `shipping_method`  tinyint(2)    NOT NULL DEFAULT 0  COMMENT '默认运输方式（0-未设置、1-海运整柜、2-海运拼箱、3-空运、4-国际快递、5-铁路、6-陆运）' AFTER `credit_currency`,
    ADD COLUMN `destination_port` varchar(100)  NOT NULL DEFAULT '' COMMENT '默认目的港' AFTER `shipping_method`,
    ADD COLUMN `remark`           varchar(500)  NOT NULL DEFAULT '' COMMENT '备注' AFTER `destination_port`,
    ADD KEY `idx_customer_code` (`customer_code`),
    ADD KEY `idx_dup_check` (`tenant_id`, `country`, `name_key`),
    ADD KEY `idx_owner_id` (`owner_id`),
    ADD KEY `idx_customer_role` (`customer_role`),
    ADD KEY `idx_customer_grade` (`customer_grade`),
    ADD KEY `idx_source_channel` (`source_channel`);

-- 存量客户补齐编码；LPAD 遇到超过 5 位的主键会截断，所以只对短主键补零
UPDATE `customer`
SET `customer_code` = CONCAT('CUS', IF(LENGTH(`id`) >= 5, `id`, LPAD(`id`, 5, '0')))
WHERE `customer_code` = '';

-- 存量客户按创建人匹配负责业务员，匹配不到保持 0（未分配，仅全部数据权限可见）；name_key 由应用启动时补算
UPDATE `customer` c
JOIN `user_basic` u ON u.`username` = c.`create_by` AND u.`tenant_id` = c.`tenant_id`
SET c.`owner_id` = u.`id`
WHERE c.`owner_id` = 0;

CREATE TABLE `customer_party`
(
    `id`               bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID',
    `customer_id`      bigint(20)   NOT NULL DEFAULT 0  COMMENT '客户ID，关联customer.id',
    `party_type`       tinyint(2)   NOT NULL DEFAULT 1  COMMENT '类型（1-收货人Consignee、2-通知方Notify Party、3-发票抬头Bill To）',
    `company_name`     varchar(200) NOT NULL DEFAULT '' COMMENT '公司名称（英文）',
    `country`          varchar(64)  NOT NULL DEFAULT '' COMMENT '国家/地区（系统国家清单英文名）',
    `state`            varchar(100) NOT NULL DEFAULT '' COMMENT '州/省',
    `city`             varchar(100) NOT NULL DEFAULT '' COMMENT '城市',
    `postcode`         varchar(20)  NOT NULL DEFAULT '' COMMENT '邮编',
    `address`          varchar(300) NOT NULL DEFAULT '' COMMENT '详细地址（英文）',
    `contact_name`     varchar(100) NOT NULL DEFAULT '' COMMENT '联系人',
    `phone`            varchar(30)  NOT NULL DEFAULT '' COMMENT '电话（国际格式）',
    `email`            varchar(100) NOT NULL DEFAULT '' COMMENT '邮箱',
    `tax_id`           varchar(50)  NOT NULL DEFAULT '' COMMENT '税号',
    `destination_port` varchar(100) NOT NULL DEFAULT '' COMMENT '目的港（仅收货人使用）',
    `is_default`       tinyint(1)   NOT NULL DEFAULT 0  COMMENT '是否该类型的默认记录（0-否、1-是）',
    `remark`           varchar(200) NOT NULL DEFAULT '' COMMENT '备注',
    `deleted_at`       datetime     NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`        varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`        varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '客户单证主体表（收货人/通知方/发票抬头）';

-- 客户管理按钮（pid=100061）：转移、导出
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`) VALUES
(110145, 100061, 'RS3110145', '转移客户', 3, 5, '','','','', '', 'partner:customer:transfer', 1,'','sys','sys'),
(110146, 100061, 'RS3110146', '导出客户', 3, 6, '','','','', '', 'partner:customer:export',   1,'','sys','sys');

UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(`menu_ids`, '$', 110145), '$', 110146)
WHERE `name` IN ('标准版', '旗舰版');
