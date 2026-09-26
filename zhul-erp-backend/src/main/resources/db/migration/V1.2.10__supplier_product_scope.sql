-- V1.2.10：供应商主营产品（品牌 → 细分品类）与统一词表：品类两级 + 中文名、品牌别名。
-- 见 openspec/changes/add-supplier-brand-category/。
ALTER TABLE `product_category`
    ADD COLUMN `category_name_zh` varchar(32) NOT NULL DEFAULT '' COMMENT '中文名称（细分品类必填，一级品类选填；业务界面优先显示）' AFTER `category_name`,
    MODIFY COLUMN `parent_id` bigint(20) NULL COMMENT '上级品类ID，关联product_category.id；NULL为一级品类（独立站品类），非空为细分品类（最多两级），细分品类只用于供应商主营产品与询盘匹配';

CREATE TABLE `product_brand_alias`
(
    `id`          bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`   int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID（0=平台级共享，本表数据均为0）',
    `brand_id`    bigint(20)   NOT NULL DEFAULT 0  COMMENT '品牌ID，关联product_brand.id',
    `alias`       varchar(64)  NOT NULL DEFAULT '' COMMENT '别名原文，如 西门子 / SIEMENS AG',
    `alias_key`   varchar(64)  NOT NULL DEFAULT '' COMMENT '比较键：去首尾空格并转小写；与品牌名称的比较键在平台内共同唯一（交叉唯一由应用层校验）',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`   varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_alias_key` (`tenant_id`, `alias_key`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_brand_id` (`brand_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '品牌别名表';

CREATE TABLE `supplier_product_scope`
(
    `id`                 bigint(20)  NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`          int(11)     NOT NULL DEFAULT 0  COMMENT '租户ID',
    `supplier_id`        bigint(20)  NOT NULL DEFAULT 0  COMMENT '供应商ID，关联supplier.id',
    `brand_id`           bigint(20)  NULL                COMMENT '品牌ID，关联product_brand.id；NULL表示待确认品牌（见pending_brand_name）',
    `pending_brand_name` varchar(64) NOT NULL DEFAULT '' COMMENT '待确认品牌名（品牌清单中没有、业务员手填的名称），brand_id为空时有值',
    `pending_key`        varchar(64) NOT NULL DEFAULT '' COMMENT '待确认品牌名比较键：去首尾空格并转小写，用于跨供应商汇总与确认',
    `category_id`        bigint(20)  NULL                COMMENT '细分品类ID，关联product_category.id（只能是二级品类）；NULL表示该品牌全部品类',
    `deleted_at`         datetime    NULL                COMMENT '软删除时间，NULL表示未删除',
    `create_time`        datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`          varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`        datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`          varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_supplier` (`tenant_id`, `supplier_id`),
    KEY `idx_brand_id` (`brand_id`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_pending_key` (`pending_key`),
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '供应商主营产品表（一行=供应商×品牌×细分品类，品类为空表示该品牌全部品类）';

-- 初始数据：以《公司主营产品》表为准（品类 -> 品牌 -> 系列），全部按编码 / 名称幂等写入，已存在的记录不改动。
-- 一级品类只有表中的 4 类；细分品类为这 4 类下供应商主营和询盘匹配用到的细分。

INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`)
SELECT 0, 'plc', 'Programmable Logic Controller', '可编程控制器', NULL, 10, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `product_category` WHERE `tenant_id` = 0 AND `category_code` = 'plc');
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`)
SELECT 0, 'hmi', 'Human Machine Interface', '人机界面', NULL, 20, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `product_category` WHERE `tenant_id` = 0 AND `category_code` = 'hmi');
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`)
SELECT 0, 'inverter', 'AC Inverter', '变频器', NULL, 30, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `product_category` WHERE `tenant_id` = 0 AND `category_code` = 'inverter');
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`)
SELECT 0, 'servo', 'AC Servo', '伺服', NULL, 40, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `product_category` WHERE `tenant_id` = 0 AND `category_code` = 'servo');
-- 各环境里已有的同编码一级品类（如开发库的 plc）只补中文名
UPDATE `product_category` SET `category_name_zh` = '可编程控制器' WHERE `tenant_id` = 0 AND `category_code` = 'plc' AND `parent_id` IS NULL AND `category_name_zh` = '';
UPDATE `product_category` SET `category_name_zh` = '人机界面' WHERE `tenant_id` = 0 AND `category_code` = 'hmi' AND `parent_id` IS NULL AND `category_name_zh` = '';
UPDATE `product_category` SET `category_name_zh` = '变频器' WHERE `tenant_id` = 0 AND `category_code` = 'inverter' AND `parent_id` IS NULL AND `category_name_zh` = '';
UPDATE `product_category` SET `category_name_zh` = '伺服' WHERE `tenant_id` = 0 AND `category_code` = 'servo' AND `parent_id` IS NULL AND `category_name_zh` = '';

INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`)
SELECT 0, 'plc_cpu', 'PLC CPU', 'PLC', p.`id`, 10, 1 FROM `product_category` p
WHERE p.`tenant_id` = 0 AND p.`category_code` = 'plc' AND p.`parent_id` IS NULL AND p.`deleted_at` IS NULL
  AND NOT EXISTS (SELECT 1 FROM (SELECT `category_code` FROM `product_category` WHERE `tenant_id` = 0) c WHERE c.`category_code` = 'plc_cpu');
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`)
SELECT 0, 'io_module', 'I/O Modules', 'I/O模块', p.`id`, 20, 1 FROM `product_category` p
WHERE p.`tenant_id` = 0 AND p.`category_code` = 'plc' AND p.`parent_id` IS NULL AND p.`deleted_at` IS NULL
  AND NOT EXISTS (SELECT 1 FROM (SELECT `category_code` FROM `product_category` WHERE `tenant_id` = 0) c WHERE c.`category_code` = 'io_module');
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`)
SELECT 0, 'comm_module', 'Communication Modules', '通信模块', p.`id`, 30, 1 FROM `product_category` p
WHERE p.`tenant_id` = 0 AND p.`category_code` = 'plc' AND p.`parent_id` IS NULL AND p.`deleted_at` IS NULL
  AND NOT EXISTS (SELECT 1 FROM (SELECT `category_code` FROM `product_category` WHERE `tenant_id` = 0) c WHERE c.`category_code` = 'comm_module');
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`)
SELECT 0, 'industrial_network', 'Industrial Networking', '工业网络', p.`id`, 40, 1 FROM `product_category` p
WHERE p.`tenant_id` = 0 AND p.`category_code` = 'plc' AND p.`parent_id` IS NULL AND p.`deleted_at` IS NULL
  AND NOT EXISTS (SELECT 1 FROM (SELECT `category_code` FROM `product_category` WHERE `tenant_id` = 0) c WHERE c.`category_code` = 'industrial_network');
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`)
SELECT 0, 'hmi_panel', 'HMI Panels', 'HMI', p.`id`, 10, 1 FROM `product_category` p
WHERE p.`tenant_id` = 0 AND p.`category_code` = 'hmi' AND p.`parent_id` IS NULL AND p.`deleted_at` IS NULL
  AND NOT EXISTS (SELECT 1 FROM (SELECT `category_code` FROM `product_category` WHERE `tenant_id` = 0) c WHERE c.`category_code` = 'hmi_panel');
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`)
SELECT 0, 'vfd', 'Variable Frequency Drives', '变频器', p.`id`, 10, 1 FROM `product_category` p
WHERE p.`tenant_id` = 0 AND p.`category_code` = 'inverter' AND p.`parent_id` IS NULL AND p.`deleted_at` IS NULL
  AND NOT EXISTS (SELECT 1 FROM (SELECT `category_code` FROM `product_category` WHERE `tenant_id` = 0) c WHERE c.`category_code` = 'vfd');
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`)
SELECT 0, 'soft_starter', 'Soft Starters', '软启动器', p.`id`, 20, 1 FROM `product_category` p
WHERE p.`tenant_id` = 0 AND p.`category_code` = 'inverter' AND p.`parent_id` IS NULL AND p.`deleted_at` IS NULL
  AND NOT EXISTS (SELECT 1 FROM (SELECT `category_code` FROM `product_category` WHERE `tenant_id` = 0) c WHERE c.`category_code` = 'soft_starter');
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`)
SELECT 0, 'servo_drive', 'Servo Drives', '伺服驱动器', p.`id`, 10, 1 FROM `product_category` p
WHERE p.`tenant_id` = 0 AND p.`category_code` = 'servo' AND p.`parent_id` IS NULL AND p.`deleted_at` IS NULL
  AND NOT EXISTS (SELECT 1 FROM (SELECT `category_code` FROM `product_category` WHERE `tenant_id` = 0) c WHERE c.`category_code` = 'servo_drive');
INSERT INTO `product_category` (`tenant_id`, `category_code`, `category_name`, `category_name_zh`, `parent_id`, `sort_order`, `status`)
SELECT 0, 'servo_motor', 'Servo Motors', '伺服电机', p.`id`, 20, 1 FROM `product_category` p
WHERE p.`tenant_id` = 0 AND p.`category_code` = 'servo' AND p.`parent_id` IS NULL AND p.`deleted_at` IS NULL
  AND NOT EXISTS (SELECT 1 FROM (SELECT `category_code` FROM `product_category` WHERE `tenant_id` = 0) c WHERE c.`category_code` = 'servo_motor');

-- 品牌：名称或别名已存在就不再创建（名称比较不分大小写）
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `status`)
SELECT 0, 'Mitsubishi', 'Japan', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'mitsubishi')
  AND NOT EXISTS (SELECT 1 FROM `product_brand_alias` WHERE `tenant_id` = 0 AND `alias_key` = 'mitsubishi');
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `status`)
SELECT 0, 'Omron', 'Japan', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'omron')
  AND NOT EXISTS (SELECT 1 FROM `product_brand_alias` WHERE `tenant_id` = 0 AND `alias_key` = 'omron');
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `status`)
SELECT 0, 'Panasonic', 'Japan', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'panasonic')
  AND NOT EXISTS (SELECT 1 FROM `product_brand_alias` WHERE `tenant_id` = 0 AND `alias_key` = 'panasonic');
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `status`)
SELECT 0, 'Delta', 'Taiwan, China', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'delta')
  AND NOT EXISTS (SELECT 1 FROM `product_brand_alias` WHERE `tenant_id` = 0 AND `alias_key` = 'delta');
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `status`)
SELECT 0, 'LS Electric', 'South Korea', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'ls electric')
  AND NOT EXISTS (SELECT 1 FROM `product_brand_alias` WHERE `tenant_id` = 0 AND `alias_key` = 'ls electric');
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `status`)
SELECT 0, 'Siemens', 'Germany', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'siemens')
  AND NOT EXISTS (SELECT 1 FROM `product_brand_alias` WHERE `tenant_id` = 0 AND `alias_key` = 'siemens');
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `status`)
SELECT 0, 'Allen-Bradley', 'United States', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'allen-bradley')
  AND NOT EXISTS (SELECT 1 FROM `product_brand_alias` WHERE `tenant_id` = 0 AND `alias_key` = 'allen-bradley');
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `status`)
SELECT 0, 'Schneider Electric', 'France', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'schneider electric')
  AND NOT EXISTS (SELECT 1 FROM `product_brand_alias` WHERE `tenant_id` = 0 AND `alias_key` = 'schneider electric');
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `status`)
SELECT 0, 'Wecon', 'China', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'wecon')
  AND NOT EXISTS (SELECT 1 FROM `product_brand_alias` WHERE `tenant_id` = 0 AND `alias_key` = 'wecon');
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `status`)
SELECT 0, 'Weintek', 'Taiwan, China', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'weintek')
  AND NOT EXISTS (SELECT 1 FROM `product_brand_alias` WHERE `tenant_id` = 0 AND `alias_key` = 'weintek');
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `status`)
SELECT 0, 'Pro-face', 'Japan', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'pro-face')
  AND NOT EXISTS (SELECT 1 FROM `product_brand_alias` WHERE `tenant_id` = 0 AND `alias_key` = 'pro-face');
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `status`)
SELECT 0, 'Hitech', 'Taiwan, China', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'hitech')
  AND NOT EXISTS (SELECT 1 FROM `product_brand_alias` WHERE `tenant_id` = 0 AND `alias_key` = 'hitech');
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `status`)
SELECT 0, 'Xinje', 'China', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'xinje')
  AND NOT EXISTS (SELECT 1 FROM `product_brand_alias` WHERE `tenant_id` = 0 AND `alias_key` = 'xinje');
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `status`)
SELECT 0, 'ABB', 'Switzerland', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'abb')
  AND NOT EXISTS (SELECT 1 FROM `product_brand_alias` WHERE `tenant_id` = 0 AND `alias_key` = 'abb');
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `status`)
SELECT 0, 'Lenze', 'Germany', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'lenze')
  AND NOT EXISTS (SELECT 1 FROM `product_brand_alias` WHERE `tenant_id` = 0 AND `alias_key` = 'lenze');
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `status`)
SELECT 0, 'Danfoss', 'Denmark', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'danfoss')
  AND NOT EXISTS (SELECT 1 FROM `product_brand_alias` WHERE `tenant_id` = 0 AND `alias_key` = 'danfoss');
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `status`)
SELECT 0, 'Vacon', 'Finland', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'vacon')
  AND NOT EXISTS (SELECT 1 FROM `product_brand_alias` WHERE `tenant_id` = 0 AND `alias_key` = 'vacon');
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `status`)
SELECT 0, 'Fuji Electric', 'Japan', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'fuji electric')
  AND NOT EXISTS (SELECT 1 FROM `product_brand_alias` WHERE `tenant_id` = 0 AND `alias_key` = 'fuji electric');
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `status`)
SELECT 0, 'Hitachi', 'Japan', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'hitachi')
  AND NOT EXISTS (SELECT 1 FROM `product_brand_alias` WHERE `tenant_id` = 0 AND `alias_key` = 'hitachi');
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `status`)
SELECT 0, 'Yaskawa', 'Japan', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'yaskawa')
  AND NOT EXISTS (SELECT 1 FROM `product_brand_alias` WHERE `tenant_id` = 0 AND `alias_key` = 'yaskawa');
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `status`)
SELECT 0, 'Toshiba', 'Japan', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'toshiba')
  AND NOT EXISTS (SELECT 1 FROM `product_brand_alias` WHERE `tenant_id` = 0 AND `alias_key` = 'toshiba');
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `status`)
SELECT 0, 'Bosch Rexroth', 'Germany', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'bosch rexroth')
  AND NOT EXISTS (SELECT 1 FROM `product_brand_alias` WHERE `tenant_id` = 0 AND `alias_key` = 'bosch rexroth');
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `status`)
SELECT 0, 'TECO', 'Taiwan, China', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'teco')
  AND NOT EXISTS (SELECT 1 FROM `product_brand_alias` WHERE `tenant_id` = 0 AND `alias_key` = 'teco');
INSERT INTO `product_brand` (`tenant_id`, `brand_name`, `country`, `status`)
SELECT 0, 'HIDER', '', 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'hider')
  AND NOT EXISTS (SELECT 1 FROM `product_brand_alias` WHERE `tenant_id` = 0 AND `alias_key` = 'hider');

-- 品牌别名：别名不能与任何品牌名称或已有别名重复
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, '三菱', '三菱' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'mitsubishi' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'mitsubishi')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = '三菱')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = '三菱');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, '三菱电机', '三菱电机' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'mitsubishi' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'mitsubishi')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = '三菱电机')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = '三菱电机');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, 'Mitsubishi Electric', 'mitsubishi electric' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'mitsubishi' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'mitsubishi')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'mitsubishi electric')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = 'mitsubishi electric');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, '欧姆龙', '欧姆龙' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'omron' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'omron')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = '欧姆龙')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = '欧姆龙');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, '松下', '松下' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'panasonic' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'panasonic')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = '松下')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = '松下');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, '台达', '台达' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'delta' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'delta')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = '台达')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = '台达');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, 'LS', 'ls' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'ls electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'ls electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'ls')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = 'ls');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, 'LSIS', 'lsis' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'ls electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'ls electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'lsis')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = 'lsis');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, 'LS产电', 'ls产电' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'ls electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'ls electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'ls产电')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = 'ls产电');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, '西门子', '西门子' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'siemens' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'siemens')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = '西门子')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = '西门子');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, 'Allen Bradley', 'allen bradley' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'allen-bradley' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'allen-bradley')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'allen bradley')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = 'allen bradley');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, 'AB', 'ab' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'allen-bradley' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'allen-bradley')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'ab')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = 'ab');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, '罗克韦尔', '罗克韦尔' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'allen-bradley' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'allen-bradley')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = '罗克韦尔')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = '罗克韦尔');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, 'Schneider', 'schneider' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'schneider electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'schneider electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'schneider')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = 'schneider');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, '施耐德', '施耐德' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'schneider electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'schneider electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = '施耐德')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = '施耐德');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, '维控', '维控' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'wecon' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'wecon')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = '维控')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = '维控');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, 'WE!NTEK', 'we!ntek' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'weintek' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'weintek')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'we!ntek')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = 'we!ntek');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, 'Weinview', 'weinview' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'weintek' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'weintek')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'weinview')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = 'weinview');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, '威纶通', '威纶通' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'weintek' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'weintek')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = '威纶通')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = '威纶通');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, '普洛菲斯', '普洛菲斯' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'pro-face' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'pro-face')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = '普洛菲斯')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = '普洛菲斯');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, '海泰克', '海泰克' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'hitech' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'hitech')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = '海泰克')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = '海泰克');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, 'Xinjie', 'xinjie' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'xinje' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'xinje')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'xinjie')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = 'xinjie');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, '信捷', '信捷' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'xinje' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'xinje')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = '信捷')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = '信捷');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, '伦茨', '伦茨' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'lenze' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'lenze')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = '伦茨')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = '伦茨');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, '丹佛斯', '丹佛斯' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'danfoss' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'danfoss')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = '丹佛斯')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = '丹佛斯');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, '伟肯', '伟肯' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'vacon' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'vacon')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = '伟肯')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = '伟肯');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, '富士', '富士' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'fuji electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'fuji electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = '富士')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = '富士');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, '富士电机', '富士电机' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'fuji electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'fuji electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = '富士电机')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = '富士电机');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, '日立', '日立' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'hitachi' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'hitachi')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = '日立')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = '日立');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, '安川', '安川' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'yaskawa' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'yaskawa')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = '安川')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = '安川');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, '东芝', '东芝' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'toshiba' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'toshiba')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = '东芝')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = '东芝');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, 'Rexroth', 'rexroth' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'bosch rexroth' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'bosch rexroth')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = 'rexroth')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = 'rexroth');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, '力士乐', '力士乐' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'bosch rexroth' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'bosch rexroth')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = '力士乐')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = '力士乐');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, '博世力士乐', '博世力士乐' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'bosch rexroth' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'bosch rexroth')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = '博世力士乐')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = '博世力士乐');
INSERT INTO `product_brand_alias` (`tenant_id`, `brand_id`, `alias`, `alias_key`)
SELECT 0, t.`id`, '东元', '东元' FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'teco' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'teco')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `product_brand` WHERE `tenant_id` = 0 AND LOWER(`brand_name`) = '东元')
  AND NOT EXISTS (SELECT 1 FROM (SELECT `alias_key` FROM `product_brand_alias` WHERE `tenant_id` = 0) x WHERE x.`alias_key` = '东元');

-- 系列：挂到对应品牌，同品牌同名系列不重复创建
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'FX Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'mitsubishi' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'mitsubishi')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'FX Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'Q Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'mitsubishi' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'mitsubishi')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'Q Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'L Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'mitsubishi' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'mitsubishi')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'L Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'CC-Link', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'mitsubishi' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'mitsubishi')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'CC-Link');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'CC-Link/LT', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'mitsubishi' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'mitsubishi')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'CC-Link/LT');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'GOT1000', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'mitsubishi' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'mitsubishi')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'GOT1000');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'GOT2000', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'mitsubishi' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'mitsubishi')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'GOT2000');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'FR-D700', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'mitsubishi' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'mitsubishi')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'FR-D700');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'FR-E700', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'mitsubishi' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'mitsubishi')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'FR-E700');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'FR-F800', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'mitsubishi' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'mitsubishi')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'FR-F800');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'FR-A800', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'mitsubishi' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'mitsubishi')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'FR-A800');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'JE Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'mitsubishi' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'mitsubishi')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'JE Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'J3 Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'mitsubishi' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'mitsubishi')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'J3 Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'J4 Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'mitsubishi' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'mitsubishi')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'J4 Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'CP Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'omron' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'omron')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'CP Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'CJ Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'omron' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'omron')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'CJ Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'CS Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'omron' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'omron')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'CS Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'NX Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'omron' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'omron')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'NX Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'NJ Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'omron' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'omron')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'NJ Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'Zen Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'omron' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'omron')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'Zen Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'Remote I/O DRT', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'omron' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'omron')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'Remote I/O DRT');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'NS Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'omron' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'omron')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'NS Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'NB Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'omron' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'omron')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'NB Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'MX2', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'omron' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'omron')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'MX2');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'G5 Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'omron' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'omron')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'G5 Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'FPOR/FP Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'panasonic' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'panasonic')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'FPOR/FP Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'FP7 Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'panasonic' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'panasonic')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'FP7 Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'FP-X Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'panasonic' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'panasonic')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'FP-X Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'FP-2SH Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'panasonic' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'panasonic')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'FP-2SH Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'GT 02', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'panasonic' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'panasonic')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'GT 02');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'GT 05', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'panasonic' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'panasonic')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'GT 05');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'GT 12', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'panasonic' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'panasonic')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'GT 12');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'GT 32', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'panasonic' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'panasonic')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'GT 32');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'A6 Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'panasonic' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'panasonic')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'A6 Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'EH3 Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'delta' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'delta')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'EH3 Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'AH500 Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'delta' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'delta')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'AH500 Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'SS2/SA2/SX2', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'delta' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'delta')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'SS2/SA2/SX2');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'SV2 Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'delta' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'delta')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'SV2 Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'ES2/EX2 Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'delta' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'delta')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'ES2/EX2 Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'EC3 Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'delta' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'delta')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'EC3 Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'DOP-100 Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'delta' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'delta')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'DOP-100 Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'VFD-E', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'delta' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'delta')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'VFD-E');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'CP-2000', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'delta' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'delta')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'CP-2000');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'MS300', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'delta' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'delta')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'MS300');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'VFD-B', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'delta' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'delta')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'VFD-B');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'C-2000', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'delta' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'delta')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'C-2000');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'MH300', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'delta' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'delta')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'MH300');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'ASDA-A2', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'delta' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'delta')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'ASDA-A2');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'ASDA-B2', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'delta' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'delta')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'ASDA-B2');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'MASTER-K/GLOFA(K200S/K300S/GM6/GM4)', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'ls electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'ls electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'MASTER-K/GLOFA(K200S/K300S/GM6/GM4)');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'MASTER-K/GLOFA(K10S1/K80S/K120S/GM7/GM7U)', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'ls electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'ls electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'MASTER-K/GLOFA(K10S1/K80S/K120S/GM7/GM7U)');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'XGT Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'ls electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'ls electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'XGT Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'XGB Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'ls electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'ls electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'XGB Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'eXP Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'ls electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'ls electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'eXP Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'iXP Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'ls electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'ls electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'iXP Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'XP Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'ls electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'ls electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'XP Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'IE5/IC5', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'ls electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'ls electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'IE5/IC5');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'IG5A', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'ls electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'ls electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'IG5A');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'IP5A', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'ls electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'ls electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'IP5A');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'S100', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'ls electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'ls electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'S100');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'H100', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'ls electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'ls electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'H100');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'IS7', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'ls electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'ls electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'IS7');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'IV5', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'ls electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'ls electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'IV5');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'LOGO!', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'siemens' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'siemens')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'LOGO!');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'S7-1200', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'siemens' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'siemens')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'S7-1200');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'S7-300', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'siemens' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'siemens')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'S7-300');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'S7-400', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'siemens' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'siemens')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'S7-400');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'ET-200', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'siemens' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'siemens')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'ET-200');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'Basic Panel(KTP)', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'siemens' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'siemens')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'Basic Panel(KTP)');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'Comfort Panel', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'siemens' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'siemens')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'Comfort Panel');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'TP/OP', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'siemens' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'siemens')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'TP/OP');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'V20', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'siemens' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'siemens')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'V20');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'G120', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'siemens' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'siemens')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'G120');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'G120C', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'siemens' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'siemens')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'G120C');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'Micro Master420', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'siemens' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'siemens')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'Micro Master420');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'Micro Master430', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'siemens' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'siemens')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'Micro Master430');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'Micro Master440', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'siemens' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'siemens')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'Micro Master440');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, '3RW30', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'siemens' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'siemens')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = '3RW30');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, '3RW44', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'siemens' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'siemens')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = '3RW44');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, '3RW40', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'siemens' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'siemens')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = '3RW40');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'SINAMICS V90', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'siemens' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'siemens')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'SINAMICS V90');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'Micro800 Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'allen-bradley' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'allen-bradley')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'Micro800 Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'Micrologix Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'allen-bradley' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'allen-bradley')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'Micrologix Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'Compactlogix Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'allen-bradley' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'allen-bradley')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'Compactlogix Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'SLC-500 Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'allen-bradley' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'allen-bradley')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'SLC-500 Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'PLC-5 Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'allen-bradley' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'allen-bradley')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'PLC-5 Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'Flex I/O', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'allen-bradley' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'allen-bradley')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'Flex I/O');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'Controllogix Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'allen-bradley' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'allen-bradley')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'Controllogix Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'PanelView 5000 Graphic Terminals', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'allen-bradley' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'allen-bradley')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'PanelView 5000 Graphic Terminals');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'PanelView Plus 7 Graphic Terminals', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'allen-bradley' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'allen-bradley')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'PanelView Plus 7 Graphic Terminals');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'PanelView Plus 6 Graphic Terminals', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'allen-bradley' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'allen-bradley')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'PanelView Plus 6 Graphic Terminals');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'PanelView Plus 6 Compact Graphic Terminals', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'allen-bradley' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'allen-bradley')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'PanelView Plus 6 Compact Graphic Terminals');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'PanelView 800 Graphic Terminals', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'allen-bradley' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'allen-bradley')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'PanelView 800 Graphic Terminals');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'PanelView Compact Graphic Terminals', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'allen-bradley' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'allen-bradley')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'PanelView Compact Graphic Terminals');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'PowerFlex 4M', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'allen-bradley' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'allen-bradley')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'PowerFlex 4M');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'PowerFlex 4', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'allen-bradley' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'allen-bradley')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'PowerFlex 4');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'PowerFlex 40', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'allen-bradley' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'allen-bradley')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'PowerFlex 40');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'PowerFlex 40P', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'allen-bradley' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'allen-bradley')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'PowerFlex 40P');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'PowerFlex 400', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'allen-bradley' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'allen-bradley')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'PowerFlex 400');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'PowerFlex 523', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'allen-bradley' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'allen-bradley')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'PowerFlex 523');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'PowerFlex 525', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'allen-bradley' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'allen-bradley')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'PowerFlex 525');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'PowerFlex 527', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'allen-bradley' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'allen-bradley')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'PowerFlex 527');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'PowerFlex 753', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'allen-bradley' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'allen-bradley')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'PowerFlex 753');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'PowerFlex 755', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'allen-bradley' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'allen-bradley')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'PowerFlex 755');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'TM221/241/251 Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'schneider electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'schneider electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'TM221/241/251 Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'M100/M200 Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'schneider electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'schneider electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'M100/M200 Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'ZELIO Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'schneider electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'schneider electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'ZELIO Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'M340/M580 Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'schneider electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'schneider electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'M340/M580 Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'TSX Micro', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'schneider electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'schneider electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'TSX Micro');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'PREMIUM', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'schneider electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'schneider electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'PREMIUM');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'QUANTUM', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'schneider electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'schneider electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'QUANTUM');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'ATV12', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'schneider electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'schneider electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'ATV12');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'ATV212', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'schneider electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'schneider electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'ATV212');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'ATV312', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'schneider electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'schneider electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'ATV312');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'ATV71', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'schneider electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'schneider electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'ATV71');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'ATV61', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'schneider electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'schneider electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'ATV61');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'ATV310', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'schneider electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'schneider electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'ATV310');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'ATV320', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'schneider electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'schneider electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'ATV320');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'ATV610', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'schneider electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'schneider electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'ATV610');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'ATV930', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'schneider electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'schneider electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'ATV930');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'ATS01/ATV22', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'schneider electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'schneider electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'ATS01/ATV22');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'ATV48', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'schneider electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'schneider electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'ATV48');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'Lexium 32', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'schneider electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'schneider electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'Lexium 32');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'Lexium 28', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'schneider electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'schneider electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'Lexium 28');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'Lexium 26', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'schneider electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'schneider electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'Lexium 26');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'Lexium 16D', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'schneider electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'schneider electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'Lexium 16D');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'iE Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'weintek' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'weintek')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'iE Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'iP Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'weintek' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'weintek')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'iP Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'eMT Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'weintek' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'weintek')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'eMT Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'CMT Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'weintek' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'weintek')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'CMT Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'GP4000 Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'pro-face' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'pro-face')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'GP4000 Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'SP5000 Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'pro-face' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'pro-face')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'SP5000 Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'FP5000 Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'pro-face' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'pro-face')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'FP5000 Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'LT4000M Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'pro-face' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'pro-face')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'LT4000M Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'LM48 Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'pro-face' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'pro-face')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'LM48 Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'PWS Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'hitech' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'hitech')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'PWS Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'ACS 150', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'abb' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'abb')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'ACS 150');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'ACS 310', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'abb' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'abb')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'ACS 310');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'ACS 355', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'abb' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'abb')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'ACS 355');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'ACS 380', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'abb' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'abb')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'ACS 380');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'ACS 530', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'abb' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'abb')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'ACS 530');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'ACS 580', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'abb' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'abb')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'ACS 580');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'ACS 880', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'abb' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'abb')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'ACS 880');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'PSR', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'abb' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'abb')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'PSR');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'PSE', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'abb' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'abb')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'PSE');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'PSTX', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'abb' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'abb')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'PSTX');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'i500', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'lenze' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'lenze')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'i500');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'FC51', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'danfoss' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'danfoss')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'FC51');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'FC101', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'danfoss' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'danfoss')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'FC101');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'FC102', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'danfoss' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'danfoss')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'FC102');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'FC111', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'danfoss' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'danfoss')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'FC111');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'FC202', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'danfoss' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'danfoss')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'FC202');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'FC301', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'danfoss' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'danfoss')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'FC301');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'FC302', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'danfoss' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'danfoss')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'FC302');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'FC360', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'danfoss' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'danfoss')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'FC360');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'MCD201', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'danfoss' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'danfoss')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'MCD201');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'MCD202', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'danfoss' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'danfoss')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'MCD202');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'MCD500', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'danfoss' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'danfoss')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'MCD500');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'VACON 10', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'vacon' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'vacon')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'VACON 10');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'VACON 20', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'vacon' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'vacon')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'VACON 20');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'VACON 20Cold Plate', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'vacon' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'vacon')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'VACON 20Cold Plate');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'MINI[C]', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'fuji electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'fuji electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'MINI[C]');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'MEGA[G]', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'fuji electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'fuji electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'MEGA[G]');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'MULTI[E]', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'fuji electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'fuji electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'MULTI[E]');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'ECO[F]', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'fuji electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'fuji electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'ECO[F]');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'HVAC[AR]', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'fuji electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'fuji electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'HVAC[AR]');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'AEC', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'fuji electric' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'fuji electric')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'AEC');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'WJ200', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'hitachi' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'hitachi')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'WJ200');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'WJ200N', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'hitachi' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'hitachi')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'WJ200N');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'SJ700', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'hitachi' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'hitachi')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'SJ700');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'NJ600B', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'hitachi' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'hitachi')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'NJ600B');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'J1000', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'yaskawa' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'yaskawa')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'J1000');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'A1000', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'yaskawa' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'yaskawa')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'A1000');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'V1000', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'yaskawa' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'yaskawa')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'V1000');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'E1000', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'yaskawa' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'yaskawa')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'E1000');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'H1000', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'yaskawa' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'yaskawa')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'H1000');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'GA700', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'yaskawa' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'yaskawa')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'GA700');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'Sigma 7 Series', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'yaskawa' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'yaskawa')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'Sigma 7 Series');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'VFNC-3', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'toshiba' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'toshiba')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'VFNC-3');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'VFS-15', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'toshiba' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'toshiba')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'VFS-15');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'AS-1', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'toshiba' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'toshiba')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'AS-1');
INSERT INTO `product_series` (`tenant_id`, `brand_id`, `series_name`, `status`)
SELECT 0, t.`id`, 'PS-1', 1 FROM (SELECT (SELECT b.`id` FROM `product_brand` b WHERE b.`tenant_id` = 0 AND b.`deleted_at` IS NULL AND (LOWER(b.`brand_name`) = 'toshiba' OR b.`id` IN (SELECT a.`brand_id` FROM `product_brand_alias` a WHERE a.`tenant_id` = 0 AND a.`alias_key` = 'toshiba')) ORDER BY b.`id` LIMIT 1) AS `id`) t
WHERE t.`id` IS NOT NULL AND NOT EXISTS (SELECT 1 FROM (SELECT `brand_id`, `series_name` FROM `product_series` WHERE `tenant_id` = 0) s WHERE s.`brand_id` = t.`id` AND s.`series_name` = 'PS-1');
