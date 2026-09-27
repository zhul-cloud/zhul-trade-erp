-- V1.2：商品主数据（由 sql/build/sql/schema_v1.2.sql 迁移而来）
-- schema_v1.2.sql（依赖 schema_v1.sql、schema_v1.1.sql；不修改任何已有表）

-- ============================
-- v1.2.0  商品主数据
-- 新增 13 张表：品牌、品类、系列、商品、规格、型号关系、技术资料、应用场景、FAQ、图片视频、物流、海关、参考价
-- 平台级共享数据：所有行 tenant_id 固定为 0（design.md 决策 1）
-- 依赖：需先执行 schema_v1.sql、schema_v1.1.sql
-- ============================

DROP TABLE IF EXISTS `product_brand`;
CREATE TABLE `product_brand`
(
    `id`          bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)      NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台级共享，本模块所有数据均为0）',
    `brand_name`  varchar(64)  NOT NULL DEFAULT '' COMMENT '品牌名称，如Siemens/ABB',
    `country`     varchar(64)  NOT NULL DEFAULT '' COMMENT '原产国/地区',
    `logo_url`    varchar(256) NOT NULL DEFAULT '' COMMENT 'Logo图片地址',
    `brand_color` varchar(16)  NOT NULL DEFAULT '' COMMENT '品牌主题色（HEX），独立站展示用',
    `is_genuine`  tinyint(2)   NOT NULL DEFAULT 1 COMMENT '是否原厂正品品牌（0-兼容/非原厂、1-原厂正品）；为0时下游不得对该品牌商品使用"Genuine"类正品断言',
    `status`      tinyint(2)   NOT NULL DEFAULT 1 COMMENT '状态（0-禁用、1-启用）',
    `deleted_at`  datetime     NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_brand_name` (`tenant_id`, `brand_name`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '品牌主数据表';

DROP TABLE IF EXISTS `product_category`;
CREATE TABLE `product_category`
(
    `id`            bigint(20)  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     int(11)     NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台级共享，本模块所有数据均为0）',
    `category_code` varchar(32) NOT NULL DEFAULT '' COMMENT '品类编码，小写蛇形，如controllers/servo；独立站URL直接沿用，有商品后不可修改',
    `category_name` varchar(64) NOT NULL DEFAULT '' COMMENT '品类名称，如PLC & Controllers',
    `parent_id`     bigint(20)  NULL COMMENT '上级品类ID，关联product_category.id；当前只有一级品类，预留，暂不使用',
    `sort_order`    int(11)     NOT NULL DEFAULT 0 COMMENT '排序',
    `status`        tinyint(2)  NOT NULL DEFAULT 1 COMMENT '状态（0-禁用、1-启用）',
    `deleted_at`    datetime    NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`     varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`     varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_category_code` (`tenant_id`, `category_code`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '品类主数据表';

DROP TABLE IF EXISTS `product_series`;
CREATE TABLE `product_series`
(
    `id`          bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)      NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台级共享，本模块所有数据均为0）',
    `brand_id`    bigint(20)   NOT NULL DEFAULT 0 COMMENT '品牌ID，关联product_brand.id',
    `series_name` varchar(64)  NOT NULL DEFAULT '' COMMENT '系列名称，如SITOP/Sigma-7',
    `description` varchar(500) NOT NULL DEFAULT '' COMMENT '系列简介',
    `status`      tinyint(2)   NOT NULL DEFAULT 1 COMMENT '状态（0-禁用、1-启用）',
    `deleted_at`  datetime     NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_brand_series` (`tenant_id`, `brand_id`, `series_name`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_brand_id` (`brand_id`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '产品系列表';

DROP TABLE IF EXISTS `product`;
CREATE TABLE `product`
(
    `id`                bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`         int(11)      NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台级共享，本模块所有数据均为0）',
    `mpn_raw`           varchar(128) NOT NULL DEFAULT '' COMMENT '原始型号（制造商资料/询盘原文，仅去首尾空格，其余不清洗；被引用后不可修改）',
    `mpn_normalized`    varchar(128) NOT NULL DEFAULT '' COMMENT '归一化型号（NFKC后转小写，去掉所有非字母数字字符），用于去重与检索；系统生成，禁止手工编辑',
    `mpn_display`       varchar(128) NOT NULL DEFAULT '' COMMENT '展示型号，页面标题使用；默认等于mpn_raw',
    `brand_id`          bigint(20)   NOT NULL DEFAULT 0 COMMENT '品牌ID，关联product_brand.id；被引用后不可修改',
    `category_id`       bigint(20)   NOT NULL DEFAULT 0 COMMENT '品类ID，关联product_category.id',
    `series_id`         bigint(20)   NULL COMMENT '系列ID，关联product_series.id；必须属于brand_id对应品牌；未分配时为空',
    `product_name`      varchar(128) NOT NULL DEFAULT '' COMMENT '产品名称，如SITOP Power Supply',
    `short_description` varchar(500) NOT NULL DEFAULT '' COMMENT '简介，需能追溯到官方资料或询盘单，不得凭空扩写',
    `spec_summary`      varchar(300) NOT NULL DEFAULT '' COMMENT '一句话核心规格摘要，列表页展示',
    `lifecycle_status`  tinyint(2)   NOT NULL DEFAULT 6 COMMENT '生命周期（1-在产Active、2-现行Current、3-旧款Legacy、4-已停产Discontinued、5-停产无替代Obsolete、6-未知Unknown）；是否有替代型号查product_relationship',
    `lifecycle_source`  varchar(128) NOT NULL DEFAULT '' COMMENT '生命周期判断依据；lifecycle_status为4或5时必填',
    `status`            tinyint(2)   NOT NULL DEFAULT 1 COMMENT '状态（0-禁用、1-启用）',
    `deleted_at`        datetime     NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`         varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`         varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_brand_mpn` (`tenant_id`, `brand_id`, `mpn_normalized`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_mpn_normalized` (`mpn_normalized`),
    KEY `idx_brand_id` (`brand_id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_series_id` (`series_id`),
    KEY `idx_lifecycle_status` (`lifecycle_status`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品主数据表（平台共享，Part Number实体核心表）';

DROP TABLE IF EXISTS `product_specification`;
CREATE TABLE `product_specification`
(
    `id`          bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)      NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台级共享，本模块所有数据均为0）',
    `product_id`  bigint(20)   NOT NULL DEFAULT 0 COMMENT '商品ID，关联product.id',
    `spec_key`    varchar(64)  NOT NULL DEFAULT '' COMMENT '规格编码，小写蛇形，如rated_voltage；同一商品内唯一',
    `spec_label`  varchar(64)  NOT NULL DEFAULT '' COMMENT '规格显示名称，如Rated Voltage',
    `spec_value`  varchar(256) NOT NULL DEFAULT '' COMMENT '规格值（展示文本）',
    `spec_unit`   varchar(32)  NOT NULL DEFAULT '' COMMENT '单位，如V DC；与规格值分开存储',
    `source`      varchar(128) NOT NULL DEFAULT '' COMMENT '数据来源，如Manufacturer Datasheet/询盘单',
    `verified`    tinyint(2)   NOT NULL DEFAULT 0 COMMENT '是否已核实（0-未核实、1-已核实）',
    `sort_order`  int(11)      NOT NULL DEFAULT 0 COMMENT '排序',
    `deleted_at`  datetime     NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_product_spec_key` (`product_id`, `spec_key`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品规格参数表';

DROP TABLE IF EXISTS `product_relationship`;
CREATE TABLE `product_relationship`
(
    `id`                 bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`          int(11)      NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台级共享，本模块所有数据均为0）',
    `product_id`         bigint(20)   NOT NULL DEFAULT 0 COMMENT '商品ID（关系挂在哪个商品下），关联product.id',
    `related_mpn`        varchar(128) NOT NULL DEFAULT '' COMMENT '关联型号原文，可以是目录里没有的旧型号/停产型号',
    `related_product_id` bigint(20)   NULL COMMENT '关联商品ID，关联product.id；仅当关联型号在目录内时有值',
    `relationship_type`  tinyint(2)   NOT NULL DEFAULT 6 COMMENT '关系类型（1-官方直接替代、2-厂商后续型号、3-功能性替代、4-兼容、5-交叉引用、6-同系列）；3/4/5/6为对称类型，1/2非对称',
    `confidence`         tinyint(2)   NOT NULL DEFAULT 3 COMMENT '置信度（1-已验证、2-高、3-中、4-低、5-未知）；4/5时下游不得展示为"推荐替代"类强断言',
    `note`               varchar(500) NOT NULL DEFAULT '' COMMENT '关系说明文案，措辞需按relationship_type区分',
    `verified_by`        varchar(32)  NOT NULL DEFAULT '' COMMENT '核实人；confidence=1时必填',
    `verified_at`        datetime     NULL COMMENT '核实时间',
    `sort_order`         int(11)      NOT NULL DEFAULT 0 COMMENT '排序',
    `deleted_at`         datetime     NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`          varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`          varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_related_product_id` (`related_product_id`),
    KEY `idx_relationship_type` (`relationship_type`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品型号关系表（替代/兼容/交叉引用）';

DROP TABLE IF EXISTS `product_document`;
CREATE TABLE `product_document`
(
    `id`            bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     int(11)      NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台级共享，本模块所有数据均为0）',
    `product_id`    bigint(20)   NOT NULL DEFAULT 0 COMMENT '商品ID，关联product.id；资料通过商品ID强绑定，只属于一个商品',
    `document_type` tinyint(2)   NOT NULL DEFAULT 1 COMMENT '文档类型（1-Datasheet、2-Manual、3-Installation Guide、4-User Manual、5-CAD、6-Drawing、7-Brochure、8-Certificate）',
    `title`         varchar(128) NOT NULL DEFAULT '' COMMENT '文档标题',
    `file_url`      varchar(512) NOT NULL DEFAULT '' COMMENT '文件地址，仅允许http://、https://或以单个/开头的站内路径；本模块只存地址，不存文件本体',
    `language`      varchar(8)   NOT NULL DEFAULT 'en' COMMENT '语言，如en/zh/ru',
    `version`       varchar(32)  NOT NULL DEFAULT '' COMMENT '文档版本',
    `source`        varchar(128) NOT NULL DEFAULT '' COMMENT '数据来源，如Manufacturer Website',
    `verified`      tinyint(2)   NOT NULL DEFAULT 0 COMMENT '是否已核实（0-未核实、1-已核实）；仅为标记，不影响读取',
    `verified_at`   datetime     NULL COMMENT '核实时间；verified=1时由服务端填写',
    `sort_order`    int(11)      NOT NULL DEFAULT 0 COMMENT '排序',
    `deleted_at`    datetime     NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`     varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`     varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品技术资料表';

DROP TABLE IF EXISTS `product_application`;
CREATE TABLE `product_application`
(
    `id`          bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)      NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台级共享，本模块所有数据均为0）',
    `product_id`  bigint(20)   NOT NULL DEFAULT 0 COMMENT '商品ID，关联product.id',
    `title`       varchar(64)  NOT NULL DEFAULT '' COMMENT '应用场景标题，如Water & pump stations',
    `description` varchar(500) NOT NULL DEFAULT '' COMMENT '场景说明，必须基于真实产品用途，不得为SEO编造',
    `icon`        varchar(16)  NOT NULL DEFAULT '' COMMENT '图标，直接存emoji（迁移自独立站）或图标标识；展示方式由消费方决定',
    `verified`    tinyint(2)   NOT NULL DEFAULT 0 COMMENT '是否已人工核实（0-未核实、1-已核实）；仅为标记，不影响读取',
    `sort_order`  int(11)      NOT NULL DEFAULT 0 COMMENT '排序',
    `deleted_at`  datetime     NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品应用场景表';

DROP TABLE IF EXISTS `product_faq`;
CREATE TABLE `product_faq`
(
    `id`          bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)      NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台级共享，本模块所有数据均为0）',
    `product_id`  bigint(20)   NOT NULL DEFAULT 0 COMMENT '商品ID，关联product.id',
    `question`    varchar(256) NOT NULL DEFAULT '' COMMENT '问题（纯文本）',
    `answer`      text         NOT NULL COMMENT '答案（纯文本）；只描述商品本身，不含卖家自己的质保/库存/发货/联系方式承诺',
    `source`      tinyint(2)   NOT NULL DEFAULT 3 COMMENT '来源（1-品类通用模板生成、2-人工撰写或已人工审核确认、3-AI辅助生成待审核）；3的记录不对租户账号返回，需平台账号审核后改为2',
    `reviewed_by` varchar(32)  NOT NULL DEFAULT '' COMMENT '审核人；由待审核确认为2时填写',
    `reviewed_at` datetime     NULL COMMENT '审核时间',
    `sort_order`  int(11)      NOT NULL DEFAULT 0 COMMENT '排序',
    `deleted_at`  datetime     NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_source` (`source`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品FAQ表';

DROP TABLE IF EXISTS `product_media`;
CREATE TABLE `product_media`
(
    `id`           bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`    int(11)      NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台级共享，本模块所有数据均为0）',
    `product_id`   bigint(20)   NOT NULL DEFAULT 0 COMMENT '商品ID，关联product.id',
    `media_type`   tinyint(2)   NOT NULL DEFAULT 1 COMMENT '媒体类型（1-图片、2-视频）',
    `file_url`     varchar(512) NOT NULL DEFAULT '' COMMENT '文件地址：上传后为站内路径（/uploads/product/...），外链为http(s)地址；不允许其他协议',
    `storage_type` tinyint(2)   NOT NULL DEFAULT 1 COMMENT '存储方式（1-平台上传、2-外部链接）',
    `cover_url`    varchar(512) NOT NULL DEFAULT '' COMMENT '视频封面地址，仅视频使用，地址规则同file_url',
    `title`        varchar(128) NOT NULL DEFAULT '' COMMENT '标题；图片时同时作为替代文字（无障碍与SEO用）',
    `file_size`    bigint(20)   NOT NULL DEFAULT 0 COMMENT '文件大小（字节），仅上传时记录，外链为0',
    `is_main`      tinyint(2)   NOT NULL DEFAULT 0 COMMENT '是否主图（0-否、1-是）；仅图片可为1，同一商品未删除行内最多一张',
    `source`       varchar(128) NOT NULL DEFAULT '' COMMENT '来源，如 Manufacturer Website',
    `sort_order`   int(11)      NOT NULL DEFAULT 0 COMMENT '排序',
    `deleted_at`   datetime     NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`    varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`    varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_product_id` (`product_id`),
    KEY `idx_product_main` (`product_id`, `is_main`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品图片与视频表';

DROP TABLE IF EXISTS `product_logistics`;
CREATE TABLE `product_logistics`
(
    `id`                bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`         int(11)       NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台级共享，本模块所有数据均为0）',
    `product_id`        bigint(20)    NOT NULL DEFAULT 0 COMMENT '商品ID，关联product.id；一个商品最多一行',
    `net_weight_kg`     decimal(10,3) NULL COMMENT '净重（kg）；未维护为NULL，不是0',
    `gross_weight_kg`   decimal(10,3) NULL COMMENT '毛重（kg，含包装）；填写时不得小于净重',
    `length_mm`         decimal(10,1) NULL COMMENT '单品长（mm）',
    `width_mm`          decimal(10,1) NULL COMMENT '单品宽（mm）',
    `height_mm`         decimal(10,1) NULL COMMENT '单品高（mm）',
    `package_type`      varchar(32)   NOT NULL DEFAULT '' COMMENT '包装类型，如 盒装/箱装/托盘',
    `package_length_mm` decimal(10,1) NULL COMMENT '包装长（mm）',
    `package_width_mm`  decimal(10,1) NULL COMMENT '包装宽（mm）',
    `package_height_mm` decimal(10,1) NULL COMMENT '包装高（mm）',
    `package_quantity`  int(11)       NULL COMMENT '每个包装内的件数',
    `is_dangerous`      tinyint(2)    NOT NULL DEFAULT 0 COMMENT '是否危险品或含电池等限运品（0-否、1-是）',
    `shipping_note`     varchar(255)  NOT NULL DEFAULT '' COMMENT '运输备注，如 需防潮、含锂电池',
    `deleted_at`        datetime      NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`         varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`       datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`         varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_product` (`tenant_id`, `product_id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品物流信息表（一对一）';

DROP TABLE IF EXISTS `product_customs`;
CREATE TABLE `product_customs`
(
    `id`                   bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`            int(11)       NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台级共享，本模块所有数据均为0）',
    `product_id`           bigint(20)    NOT NULL DEFAULT 0 COMMENT '商品ID，关联product.id；一个商品最多一行',
    `hs_code`              varchar(10)   NOT NULL DEFAULT '' COMMENT 'HS编码，仅数字（去掉点和空格后6~10位）；一个商品一个，空表示未维护',
    `customs_name_cn`      varchar(128)  NOT NULL DEFAULT '' COMMENT '申报品名（中文）',
    `customs_name_en`      varchar(128)  NOT NULL DEFAULT '' COMMENT '申报品名（英文）',
    `origin_country`       char(2)       NOT NULL DEFAULT '' COMMENT '默认原产国，ISO 3166-1 alpha-2大写（如DE/CN）；同一型号不同批次可能不同，实际以货物单据为准',
    `declaration_elements` varchar(500)  NOT NULL DEFAULT '' COMMENT '申报要素',
    `supervision_conditions` varchar(32) NOT NULL DEFAULT '' COMMENT '监管条件代码，如A/B；无则为空',
    `export_rebate_rate`   decimal(5,2)  NULL COMMENT '出口退税率（%，0~100）；政策会调整，以最近一次维护为准；未维护为NULL',
    `deleted_at`           datetime      NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`          datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`            varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`          datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`            varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_product` (`tenant_id`, `product_id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_hs_code` (`hs_code`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品海关信息表（一对一）';

DROP TABLE IF EXISTS `product_reference_price`;
CREATE TABLE `product_reference_price`
(
    `id`             bigint(20)     NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`      int(11)        NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台级共享，本模块所有数据均为0）',
    `product_id`     bigint(20)     NOT NULL DEFAULT 0 COMMENT '商品ID，关联product.id；一个商品最多一行',
    `price_original` decimal(18, 2) NOT NULL COMMENT '参考价（原币），必须大于0；这是平台层面的参考价，不是任何租户的报价或售价',
    `currency_code`  char(3)        NOT NULL COMMENT '币种（ISO 4217，如USD/CNY）；金额必须带币种',
    `exchange_rate`  decimal(18, 6) NULL COMMENT '汇率（原币→本位币CNY）；币种为CNY时为1；非CNY且汇率未维护时为NULL',
    `price_cny`      decimal(18, 2) NULL COMMENT '参考价（本位币），由原币与汇率计算，HALF_UP保留2位；汇率未维护时为NULL（展示为"未计算"，不是0元）',
    `price_source`   varchar(128)   NOT NULL DEFAULT '' COMMENT '价格来源，如 厂商官网目录价、eBay参考价',
    `price_date`     date           NULL COMMENT '取价日期；未知为NULL',
    `deleted_at`     datetime       NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`    datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`      varchar(32)    NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`    datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`      varchar(32)    NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_product` (`tenant_id`, `product_id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品平台参考价表（一对一）';
