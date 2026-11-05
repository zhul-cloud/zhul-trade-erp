-- 客户跟进第三期（openspec change customer-followup-account）：客户经营板块（board 5）
-- 1. followup_item         来源类型新增 6-客户；采购规律、触达理由、沉睡、近 12 个月成交等字段（design D1–D5）
-- 2. customer_followup     客户经营的回答字段与到期日（design D8、D9）
-- 3. customer              战略客户与季度回顾（design D7）
-- 4. customer_annual_plan  战略客户的年度计划（design D7）
-- 5. sys_config            客户经营节奏参数

ALTER TABLE `followup_item`
    MODIFY COLUMN `target_type` tinyint(2) NOT NULL DEFAULT 1 COMMENT '来源单据类型（1-商机、2-询盘、3-报价单、4-PI、5-销售订单、6-客户）',
    MODIFY COLUMN `stage_code` varchar(16) NOT NULL DEFAULT '' COMMENT '阶段编码（S1–S7、F1–F6、A1–A6、G4–G7）',
    MODIFY COLUMN `stage_group` tinyint(2) NOT NULL DEFAULT 1 COMMENT '阶段分组（商机成交：1-报价前、2-报价后、3-PI后；订单履约：4-采购中、5-验收发货、6-在途；售后评价：7-签收、8-使用、9-问题、10-满意评价；客户经营：11-需求挖掘、12-复购机会、13-交叉销售、14-长期合作）',
    MODIFY COLUMN `close_reason` tinyint(2) NOT NULL DEFAULT 0 COMMENT '移出原因（0-未移出、1-单据结束、2-被新单据接替、3-商机结束、4-单据删除或作废、5-售后结束、6-不再是成交客户）',
    ADD COLUMN `pattern_cycle_days` int(11) NOT NULL DEFAULT 0 COMMENT '客户经营：采购周期（天），采购少于 3 次时为 0' AFTER `after_sales_id`,
    ADD COLUMN `pattern_next_date` date NULL COMMENT '客户经营：预计下次采购日，采购少于 3 次时为空' AFTER `pattern_cycle_days`,
    ADD COLUMN `pattern_text` varchar(100) NOT NULL DEFAULT '' COMMENT '客户经营：采购规律说明，如「约每 90 天一次，下次约 1 月」「数据不足」' AFTER `pattern_next_date`,
    ADD COLUMN `pattern_brands` varchar(200) NOT NULL DEFAULT '' COMMENT '客户经营：近 12 个月按金额排前 3 的品牌和品类，如「Siemens、ABB · PLC、变频器」' AFTER `pattern_text`,
    ADD COLUMN `recent_order_count` int(11) NOT NULL DEFAULT 0 COMMENT '客户经营：近 12 个月订单数（不含已取消和售后补发；近 12 个月本位币金额存 amount_cny）' AFTER `pattern_brands`,
    ADD COLUMN `last_order_date` date NULL COMMENT '客户经营：最近一单的下单日期' AFTER `recent_order_count`,
    ADD COLUMN `account_reason` varchar(100) NOT NULL DEFAULT '' COMMENT '客户经营：触达理由，如「A 级每 14 天联系」「预计 1 月采购，提前问计划」' AFTER `last_order_date`,
    ADD COLUMN `account_need` tinyint(2) NOT NULL DEFAULT 0 COMMENT '客户经营：记跟进选了「客户有新需求」还没建询盘（0-否、1-是），建询盘后清零' AFTER `account_reason`,
    ADD COLUMN `account_cross` tinyint(2) NOT NULL DEFAULT 0 COMMENT '客户经营：记跟进选了「聊了新品类」（0-否、1-是），选「当前没有需求」或建询盘后清零' AFTER `account_need`,
    ADD COLUMN `cross_category` varchar(50) NOT NULL DEFAULT '' COMMENT '客户经营：聊到的新品类' AFTER `account_cross`,
    ADD COLUMN `dormant_since` date NULL COMMENT '客户经营：首次满足沉睡条件的日期，不再沉睡时清空' AFTER `cross_category`;

ALTER TABLE `customer_followup`
    MODIFY COLUMN `target_type` tinyint(2) NOT NULL DEFAULT 0 COMMENT '关联单据类型（0-无、1-商机、2-询盘、3-报价单、4-PI、5-销售订单、6-客户）',
    MODIFY COLUMN `situation` int(11) NOT NULL DEFAULT 0 COMMENT '客户情况位掩码（1-给了反馈、2-在谈条件、4-没回复、8-客户有新需求、16-聊了新品类、32-当前没有需求）',
    ADD COLUMN `account_answer` tinyint(2) NOT NULL DEFAULT 0 COMMENT '客户经营的客户情况（0-无、1-客户有新需求、2-聊了新品类、3-当前没有需求、4-没回复）' AFTER `review_platform`,
    ADD COLUMN `cross_category` varchar(50) NOT NULL DEFAULT '' COMMENT '聊了新品类：品类' AFTER `account_answer`,
    ADD COLUMN `no_need_reason` varchar(16) NOT NULL DEFAULT '' COMMENT '当前没有需求的原因（NO_PROJECT-近期没有项目、NOT_RELEVANT-暂不相关、NO_BUDGET-预算没下来、OTHER-其他）' AFTER `cross_category`,
    ADD COLUMN `quarter_review` tinyint(2) NOT NULL DEFAULT 0 COMMENT '这次是季度回顾（0-否、1-是）' AFTER `no_need_reason`,
    ADD COLUMN `due_date` date NULL COMMENT '记跟进时跟进对象原来的下一步日期，团队视图算按时联系' AFTER `quarter_review`;

ALTER TABLE `customer`
    ADD COLUMN `is_strategic` tinyint(2) NOT NULL DEFAULT 0 COMMENT '战略客户（0-否、1-是）' AFTER `profile_done_by`,
    ADD COLUMN `strategic_at` datetime NULL COMMENT '设为战略客户的时间' AFTER `is_strategic`,
    ADD COLUMN `last_review_date` date NULL COMMENT '上次季度回顾日期' AFTER `strategic_at`;

CREATE TABLE IF NOT EXISTS `customer_annual_plan` (
    `id`            int(11)       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     int(11)       NOT NULL DEFAULT 0 COMMENT '租户ID',
    `customer_id`   bigint        NOT NULL DEFAULT 0 COMMENT '客户ID，关联customer.id',
    `plan_year`     int(11)       NOT NULL DEFAULT 0 COMMENT '计划年份，如 2026',
    `target_amount` decimal(18,2) NOT NULL DEFAULT 0.00 COMMENT '年度目标金额（本位币）',
    `currency_code` varchar(8)    NOT NULL DEFAULT 'CNY' COMMENT '目标金额币种，固定为本位币 CNY',
    `focus`         varchar(200)  NOT NULL DEFAULT '' COMMENT '重点品类或品牌',
    `note`          varchar(500)  NOT NULL DEFAULT '' COMMENT '备注',
    `deleted_at`    datetime      NULL COMMENT '软删除时间，NULL表示未删除',
    `create_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`     varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`   datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`     varchar(32)   NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_customer_year` (`tenant_id`, `customer_id`, `plan_year`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_customer_id` (`customer_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_deleted_at` (`deleted_at`)
) ENGINE=InnoDB AUTO_INCREMENT=1000 DEFAULT CHARSET=utf8mb4 COMMENT='客户年度计划（战略客户）';

INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_type`, `is_builtin`, `config_group`, `remark`, `create_by`, `update_by`)
SELECT 0, k.config_key, k.config_name, k.config_value, k.config_type, 1, 'sales', k.remark, 'sys', 'sys'
FROM (
              SELECT 'sales.followup.account.ahead-days' AS config_key, '客户跟进：预计采购提前提醒天数' AS config_name,
                     '30' AS config_value, 'INTEGER' AS config_type,
                     '客户经营：预计下次采购日前这么多天提醒联系' AS remark
    UNION ALL SELECT 'sales.followup.account.dormant-days', '客户跟进：沉睡判定天数', '180', 'INTEGER',
                     '最近一单超过这么多天，且近期没有询盘、报价、商机的成交客户标为沉睡'
    UNION ALL SELECT 'sales.followup.account.dormant-quiet-days', '客户跟进：沉睡判定的安静天数', '90', 'INTEGER',
                     '近这么多天没有新询盘、报价或商机'
    UNION ALL SELECT 'sales.followup.account.dormant-interval-days', '客户跟进：沉睡客户激活间隔', '60', 'INTEGER',
                     '沉睡客户每隔这么多天提醒一次激活'
    UNION ALL SELECT 'sales.followup.account.review-days', '客户跟进：战略客户季度回顾间隔', '90', 'INTEGER',
                     '战略客户上次回顾后这么多天提醒季度回顾'
) k
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` c WHERE c.`tenant_id` = 0 AND c.`config_key` = k.config_key);

-- 回退：
-- DELETE FROM `sys_config` WHERE `tenant_id` = 0 AND `config_key` IN ('sales.followup.account.ahead-days',
--     'sales.followup.account.dormant-days', 'sales.followup.account.dormant-quiet-days',
--     'sales.followup.account.dormant-interval-days', 'sales.followup.account.review-days');
-- （先导出年度计划）DROP TABLE `customer_annual_plan`;
-- ALTER TABLE `customer` DROP COLUMN `last_review_date`, DROP COLUMN `strategic_at`, DROP COLUMN `is_strategic`;
-- ALTER TABLE `customer_followup` DROP COLUMN `due_date`, DROP COLUMN `quarter_review`, DROP COLUMN `no_need_reason`,
--     DROP COLUMN `cross_category`, DROP COLUMN `account_answer`;
-- DELETE FROM `followup_item` WHERE `board` = 5;
-- ALTER TABLE `followup_item` DROP COLUMN `dormant_since`, DROP COLUMN `cross_category`, DROP COLUMN `account_cross`,
--     DROP COLUMN `account_need`, DROP COLUMN `account_reason`, DROP COLUMN `last_order_date`, DROP COLUMN `recent_order_count`,
--     DROP COLUMN `pattern_brands`, DROP COLUMN `pattern_text`, DROP COLUMN `pattern_next_date`, DROP COLUMN `pattern_cycle_days`;
