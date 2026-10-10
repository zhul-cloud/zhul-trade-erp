-- ===========================
-- 收款管理：付款方式（线上 / 线下）、平台收款、先到账后认领、实收金额与实收人民币
-- 见 openspec/changes/add-receipt-management
-- ===========================

ALTER TABLE `payment_receipt`
    MODIFY COLUMN `pi_id` bigint NULL COMMENT 'PI ID，关联proforma_invoice.id；为空表示未认领到账',
    ADD COLUMN `payment_method`      varchar(32)   NOT NULL DEFAULT 'TT' COMMENT '付款方式（字典 payment_method 的 item_code）' AFTER `currency_code`,
    ADD COLUMN `payment_method_name` varchar(64)   NOT NULL DEFAULT '银行转账' COMMENT '付款方式名称快照' AFTER `payment_method`,
    ADD COLUMN `channel`             tinyint(2)    NOT NULL DEFAULT 1 COMMENT '线上 / 线下（1-线下、2-线上），取自付款方式' AFTER `payment_method_name`,
    ADD COLUMN `platform_order_no`   varchar(64)   NOT NULL DEFAULT '' COMMENT '平台订单号（平台收款必填）' AFTER `channel`,
    ADD COLUMN `platform_fee`        decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '平台手续费（原币，平台收款）' AFTER `fee_diff`,
    ADD COLUMN `net_amount`          decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '实收金额（原币）= 到账金额 − 平台手续费（仅到账）' AFTER `platform_fee`,
    ADD COLUMN `net_amount_cny`      decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '实收人民币：默认实收 × 系统汇率，已结汇时为实际入账人民币（仅到账）' AFTER `net_amount`,
    ADD COLUMN `rate_source`         tinyint(2)    NOT NULL DEFAULT 1 COMMENT '汇率来源（1-系统汇率、2-实际入账反算）' AFTER `net_amount_cny`,
    ADD COLUMN `payer`               varchar(128)  NOT NULL DEFAULT '' COMMENT '付款人（未认领到账：银行流水上的名称）' AFTER `rate_source`,
    ADD COLUMN `claimed_by`          bigint        NULL COMMENT '认领人用户ID（先到账后认领）' AFTER `operator_id`,
    ADD COLUMN `claimed_at`          datetime      NULL COMMENT '认领时间' AFTER `claimed_by`,
    ADD KEY `idx_receipt_platform_order` (`tenant_id`, `platform_order_no`),
    ADD KEY `idx_receipt_date` (`receipt_date`);

-- 存量：付款方式默认银行转账（线下）；到账的实收 = 到账金额，实收人民币 = 原折算金额
UPDATE `payment_receipt`
SET `net_amount` = `amount`, `net_amount_cny` = `amount_cny`
WHERE `kind` = 2 AND `net_amount` = 0;

-- 平台字典「付款方式」：item_value 标明线上 / 线下
INSERT INTO `dict_type` (`tenant_id`, `dict_type`, `dict_name`, `is_builtin`, `status`, `remark`, `create_by`, `update_by`)
SELECT 0, 'payment_method', '付款方式', 1, 1, '收款的付款方式；字典项的值 ONLINE 为线上、OFFLINE 为线下（报税统计用），默认项为上传水单的默认值', 'sys', 'sys'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `dict_type` WHERE `dict_type` = 'payment_method' AND `tenant_id` = 0);

INSERT INTO `dict_item` (`tenant_id`, `dict_type_id`, `dict_type`, `item_code`, `item_name`, `item_name_en`, `item_value`, `sort_order`, `is_default`, `status`, `remark`, `create_by`, `update_by`)
SELECT 0, t.`id`, t.`dict_type`, v.code, v.name, v.en, v.val, v.s, v.d, 1, '', 'sys', 'sys'
FROM `dict_type` t
JOIN (
              SELECT 'TT' AS code, '银行转账' AS name, 'Bank Transfer (T/T)' AS en, 'OFFLINE' AS val, 1 AS s, 1 AS d
    UNION ALL SELECT 'WECHAT',     '微信',           'WeChat Pay',              'OFFLINE', 2, 0
    UNION ALL SELECT 'ALIPAY',     '支付宝',         'Alipay',                  'OFFLINE', 3, 0
    UNION ALL SELECT 'PAYPAL',     'PayPal',         'PayPal',                  'OFFLINE', 4, 0
    UNION ALL SELECT 'ALIBABA_TA', '阿里巴巴信用保障', 'Alibaba Trade Assurance', 'ONLINE',  5, 0
    UNION ALL SELECT 'MIC',        '中国制造网',     'Made-in-China.com',       'ONLINE',  6, 0
) v
WHERE t.`dict_type` = 'payment_method' AND t.`tenant_id` = 0
  AND NOT EXISTS (SELECT 1 FROM `dict_item` i WHERE i.`dict_type_id` = t.`id` AND i.`item_code` = v.code);

-- 菜单改名：到账登记 → 收款管理（地址不变）
UPDATE `resource` SET `name` = '收款管理', `update_time` = NOW(), `update_by` = 'sys' WHERE `id` = 100085;

-- PI 下的按钮权限：登记平台收款、认领到账
INSERT INTO `resource` (`id`,`pid`,`code`,`name`,`type`,`sort`,`light_icon`,`light_selected_icon`,`dark_icon`,`dark_selected_icon`,`path`,`permission`,`status`,`micro_app`,`create_by`,`update_by`)
SELECT v.* FROM (
              SELECT 110183 AS id, 100078 AS pid, 'RS3110183' AS code, '登记平台收款' AS name, 3 AS type, 4 AS sort, '' AS li, '' AS lsi, '' AS di, '' AS dsi,
                     '' AS path, 'sales:pi:platform-receipt' AS permission, 1 AS status, '' AS micro_app, 'sys' AS cb, 'sys' AS ub
    UNION ALL SELECT 110184, 100078, 'RS3110184', '认领到账', 3, 5, '', '', '', '', '', 'sales:pi:claim-receipt', 1, '', 'sys', 'sys'
) v
WHERE NOT EXISTS (SELECT 1 FROM `resource` r WHERE r.`id` = v.id);

UPDATE `tenant_package`
SET `menu_ids` = JSON_ARRAY_APPEND(JSON_ARRAY_APPEND(`menu_ids`, '$', 110183), '$', 110184)
WHERE `name` IN ('标准版', '旗舰版') AND NOT JSON_CONTAINS(`menu_ids`, '110183');

-- 授给已有「上传付款水单」的角色
INSERT INTO `role_resource` (`role_code`, `resource_id`, `resource_code`)
SELECT DISTINCT x.`role_code`, s.`id`, s.`code`
FROM `role_resource` x
JOIN `resource` s ON s.`id` IN (110183, 110184)
WHERE x.`resource_id` = 110179
  AND NOT EXISTS (SELECT 1 FROM `role_resource` y WHERE y.`role_code` = x.`role_code` AND y.`resource_id` = s.`id`);
