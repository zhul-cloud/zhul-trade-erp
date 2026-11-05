-- 客户跟进第二期（openspec change customer-followup-fulfillment）：订单履约、售后评价两个板块与团队视图
-- 1. followup_item      唯一键加上板块（订单履约与售后评价都指向同一张销售订单，design D1）；新增节点、异常、预计送达、售后与阶段起始字段
-- 2. customer_followup  新增履约与售后阶段的回答字段（design D8）
-- 3. sop_stage          新增参考停留天数（design D9）
-- 4. sop_blocker        新增板块（履约异常、售后问题的应对办法按板块区分）
-- 5. after_sales        新增案例沉淀
-- 6. sys_config         运输时效、评价提醒参数

ALTER TABLE `followup_item`
    DROP KEY `uk_target`,
    ADD UNIQUE KEY `uk_board_target` (`tenant_id`, `board`, `target_type`, `target_id`),
    MODIFY COLUMN `target_type` tinyint(2) NOT NULL DEFAULT 1 COMMENT '来源单据类型（1-商机、2-询盘、3-报价单、4-PI、5-销售订单）',
    MODIFY COLUMN `stage_code` varchar(16) NOT NULL DEFAULT '' COMMENT '阶段编码（S1–S7、F1–F6、A1–A6）',
    MODIFY COLUMN `stage_group` tinyint(2) NOT NULL DEFAULT 1 COMMENT '阶段分组（商机成交：1-报价前、2-报价后、3-PI后；订单履约：4-采购中、5-验收发货、6-在途；售后评价：7-签收、8-使用、9-问题、10-满意评价）',
    MODIFY COLUMN `next_action_source` tinyint(2) NOT NULL DEFAULT 1 COMMENT '下一步来源（1-系统节奏，每天按节奏刷新、2-业务员定的、3-系统一次性提醒，如培育到期或延后的系统提醒，不再按节奏刷新、4-来自售后单的下一步）',
    MODIFY COLUMN `close_reason` tinyint(2) NOT NULL DEFAULT 0 COMMENT '移出原因（0-未移出、1-单据结束、2-被新单据接替、3-商机结束、4-单据删除或作废、5-售后结束）',
    ADD COLUMN `stage_since` datetime NULL COMMENT '进入当前阶段的时间，用于停留天数' AFTER `stage_group`,
    ADD COLUMN `situation_text` varchar(200) NOT NULL DEFAULT '' COMMENT '订单履约、售后评价：系统带出的现在的情况，如「11-01 已出运，DHL 运单 1234567890」' AFTER `next_action_source`,
    ADD COLUMN `part_count` int(11) NOT NULL DEFAULT 0 COMMENT '订单履约：订单分几部分（每张采购单一部分，不经过采购的合为一部分）' AFTER `situation_text`,
    ADD COLUMN `node_code` varchar(16) NOT NULL DEFAULT '' COMMENT '订单履约：最近一个已生成提醒的节点（created-订单创建、ordered-采购下单、shot-实拍完成、shipped-出运、exception-履约异常）' AFTER `next_action_source`,
    ADD COLUMN `node_date` date NULL COMMENT '订单履约：该节点发生日期' AFTER `node_code`,
    ADD COLUMN `exception_flags` int(11) NOT NULL DEFAULT 0 COMMENT '订单履约异常位掩码（1-采购超期、2-到货差异待处理、4-出库单撤回未重建），0 为无' AFTER `node_date`,
    ADD COLUMN `exception_since` date NULL COMMENT '首次出现异常的日期，异常全部消除后清空' AFTER `exception_flags`,
    ADD COLUMN `eta_date` date NULL COMMENT '预计送达日期，NULL 为未出运' AFTER `exception_since`,
    ADD COLUMN `eta_source` tinyint(2) NOT NULL DEFAULT 0 COMMENT '预计送达来源（0-无、1-按出运日与时效估算、2-业务员填写）' AFTER `eta_date`,
    ADD COLUMN `eta_first` date NULL COMMENT '首次估算的预计送达日期，只写一次，用于按时送达统计' AFTER `eta_source`,
    ADD COLUMN `eta_note` varchar(64) NOT NULL DEFAULT '' COMMENT '预计送达依据，如「DHL · 按 5 天估算」' AFTER `eta_first`,
    ADD COLUMN `signed_date` date NULL COMMENT '售后评价：客户确认收到的日期' AFTER `eta_note`,
    ADD COLUMN `used_ok` tinyint(2) NOT NULL DEFAULT 0 COMMENT '售后评价：使用确认正常（0-否、1-是）' AFTER `signed_date`,
    ADD COLUMN `satisfaction` tinyint(2) NOT NULL DEFAULT 0 COMMENT '售后评价：满意度（0-未确认、1-满意、2-一般、3-不满意）' AFTER `used_ok`,
    ADD COLUMN `dissatisfied_reason` varchar(16) NOT NULL DEFAULT '' COMMENT '一般或不满意的原因（QUALITY-产品质量、DELIVERY-交期、COMMUNICATION-沟通、PRICE-价格、OTHER-其他）' AFTER `satisfaction`,
    ADD COLUMN `review_status` tinyint(2) NOT NULL DEFAULT 0 COMMENT '评价状态（0-未处理、1-这次先不邀请、2-已邀请、3-已评价、4-婉拒、5-未评价）' AFTER `dissatisfied_reason`,
    ADD COLUMN `review_platform` varchar(32) NOT NULL DEFAULT '' COMMENT '评价平台，如阿里国际站' AFTER `review_status`,
    ADD COLUMN `review_invited_at` date NULL COMMENT '邀请评价的日期' AFTER `review_platform`,
    ADD COLUMN `review_remind_count` int(11) NOT NULL DEFAULT 0 COMMENT '已提醒评价次数' AFTER `review_invited_at`,
    ADD COLUMN `after_sales_id` bigint NOT NULL DEFAULT 0 COMMENT '售后评价：当前关联的售后单ID，关联after_sales.id，0 为无' AFTER `review_remind_count`,
    ADD KEY `idx_board_stage` (`tenant_id`, `board`, `stage_code`),
    ADD KEY `idx_after_sales_id` (`after_sales_id`);

UPDATE `followup_item` SET `stage_since` = `create_time` WHERE `stage_since` IS NULL;

ALTER TABLE `customer_followup`
    MODIFY COLUMN `target_type` tinyint(2) NOT NULL DEFAULT 0 COMMENT '关联单据类型（0-无、1-商机、2-询盘、3-报价单、4-PI、5-销售订单）',
    ADD COLUMN `notice_mask` int(11) NOT NULL DEFAULT 0 COMMENT '订单履约：这次和客户沟通了什么（位掩码：1-运单号和发货照片、2-新交期或送达日、4-客户要我们配合、8-客户没回复）' AFTER `expected_pay_date`,
    ADD COLUMN `eta_date` date NULL COMMENT '改后的预计送达日期（履约改预计送达、签收确认还没收到）' AFTER `notice_mask`,
    ADD COLUMN `receipt_answer` tinyint(2) NOT NULL DEFAULT 0 COMMENT '签收确认（0-无、1-已收到没问题、2-已收到有问题、3-还没收到）' AFTER `eta_date`,
    ADD COLUMN `use_answer` tinyint(2) NOT NULL DEFAULT 0 COMMENT '使用确认（0-无、1-正常使用、2-有问题、3-还没开始用）' AFTER `receipt_answer`,
    ADD COLUMN `issue_types` varchar(64) NOT NULL DEFAULT '' COMMENT '问题类型（逗号分隔：少件、破损、型号不对、其他）' AFTER `use_answer`,
    ADD COLUMN `satisfaction` tinyint(2) NOT NULL DEFAULT 0 COMMENT '满意度（0-无、1-满意、2-一般、3-不满意）' AFTER `issue_types`,
    ADD COLUMN `dissatisfied_reason` varchar(16) NOT NULL DEFAULT '' COMMENT '一般或不满意的原因（QUALITY、DELIVERY、COMMUNICATION、PRICE、OTHER）' AFTER `satisfaction`,
    ADD COLUMN `review_status` tinyint(2) NOT NULL DEFAULT 0 COMMENT '评价状态（0-无、1-这次先不邀请、2-已邀请、3-已评价、4-婉拒、6-还没评）' AFTER `dissatisfied_reason`,
    ADD COLUMN `review_platform` varchar(32) NOT NULL DEFAULT '' COMMENT '评价平台' AFTER `review_status`,
    ADD KEY `idx_operator_time` (`tenant_id`, `operator_id`, `create_time`);

ALTER TABLE `sop_stage`
    ADD COLUMN `ref_days` int(11) NOT NULL DEFAULT 0 COMMENT '参考停留天数，团队视图里平均停留超过它标橙；0 为不比较' AFTER `example_dialog`;

UPDATE `sop_stage` SET `ref_days` = CASE `stage_code`
    WHEN 'S1' THEN 2 WHEN 'S2' THEN 3 WHEN 'S3' THEN 5 WHEN 'S4' THEN 7 WHEN 'S5' THEN 7 WHEN 'S6' THEN 10 WHEN 'S7' THEN 10 ELSE `ref_days` END
WHERE `tenant_id` = 0 AND `board` = 2;

ALTER TABLE `sop_blocker`
    ADD COLUMN `board` tinyint(2) NOT NULL DEFAULT 2 COMMENT '板块（2-商机成交、3-订单履约、4-售后评价）' AFTER `tenant_id`;

ALTER TABLE `after_sales`
    ADD COLUMN `case_note` varchar(500) NOT NULL DEFAULT '' COMMENT '案例沉淀：结案时选填，这次问题的经验和以后怎么避免' AFTER `result`;

INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_type`, `is_builtin`, `config_group`, `remark`, `create_by`, `update_by`)
SELECT 0, k.config_key, k.config_name, k.config_value, k.config_type, 1, 'sales', k.remark, 'sys', 'sys'
FROM (
              SELECT 'sales.followup.transit.express-carriers' AS config_key, '客户跟进：快递承运商' AS config_name,
                     'DHL,FEDEX,UPS,TNT,EMS,ARAMEX' AS config_value, 'STRING' AS config_type,
                     '出运单承运商以其中任一项开头（不区分大小写）的按快递时效估算预计送达，逗号分隔' AS remark
    UNION ALL SELECT 'sales.followup.transit.express-days', '客户跟进：快递运输天数', '5', 'INTEGER',
                     '预计送达 = 出运日 + 这么多天（快递承运商）'
    UNION ALL SELECT 'sales.followup.transit.other-days', '客户跟进：其他运输天数', '10', 'INTEGER',
                     '预计送达 = 出运日 + 这么多天（快递以外的承运商）'
    UNION ALL SELECT 'sales.followup.review.remind-days', '客户跟进：评价提醒间隔（天）', '7', 'INTEGER',
                     '邀请评价后每隔几天提醒一次看客户有没有评价'
    UNION ALL SELECT 'sales.followup.review.remind-times', '客户跟进：评价最多提醒次数', '2', 'INTEGER',
                     '提醒这么多次仍没评价，再过一个间隔后售后结束，记为未评价'
) k
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` c WHERE c.`tenant_id` = 0 AND c.`config_key` = k.config_key);

-- 回退：
-- DELETE FROM `sys_config` WHERE `tenant_id` = 0 AND `config_key` IN ('sales.followup.transit.express-carriers',
--     'sales.followup.transit.express-days', 'sales.followup.transit.other-days', 'sales.followup.review.remind-days',
--     'sales.followup.review.remind-times');
-- （先导出 case_note）ALTER TABLE `after_sales` DROP COLUMN `case_note`;
-- DELETE FROM `sop_blocker` WHERE `board` <> 2; ALTER TABLE `sop_blocker` DROP COLUMN `board`;
-- DELETE FROM `sop_stage` WHERE `board` IN (3, 4); ALTER TABLE `sop_stage` DROP COLUMN `ref_days`;
-- ALTER TABLE `customer_followup` DROP KEY `idx_operator_time`, DROP COLUMN `review_platform`, DROP COLUMN `review_status`,
--     DROP COLUMN `dissatisfied_reason`, DROP COLUMN `satisfaction`, DROP COLUMN `issue_types`, DROP COLUMN `use_answer`,
--     DROP COLUMN `receipt_answer`, DROP COLUMN `eta_date`, DROP COLUMN `notice_mask`;
-- DELETE FROM `followup_item` WHERE `board` IN (3, 4);
-- ALTER TABLE `followup_item` DROP KEY `uk_board_target`, ADD UNIQUE KEY `uk_target` (`tenant_id`, `target_type`, `target_id`),
--     DROP KEY `idx_board_stage`, DROP KEY `idx_after_sales_id`, DROP COLUMN `after_sales_id`, DROP COLUMN `review_remind_count`,
--     DROP COLUMN `review_invited_at`, DROP COLUMN `review_platform`, DROP COLUMN `review_status`, DROP COLUMN `dissatisfied_reason`,
--     DROP COLUMN `satisfaction`, DROP COLUMN `used_ok`, DROP COLUMN `signed_date`, DROP COLUMN `eta_note`, DROP COLUMN `eta_first`,
--     DROP COLUMN `eta_source`, DROP COLUMN `eta_date`, DROP COLUMN `exception_since`, DROP COLUMN `exception_flags`,
--     DROP COLUMN `node_date`, DROP COLUMN `node_code`, DROP COLUMN `situation_text`, DROP COLUMN `part_count`, DROP COLUMN `stage_since`;
