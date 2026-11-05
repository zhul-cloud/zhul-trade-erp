-- 客户跟进第四期（openspec change customer-followup-develop）：主动开发板块（board 1）
-- 1. followup_item      开发字段：为什么值得开发、来源方式、触达次数、首次触达日、回复、采购信号、停止原因（design D1）
-- 2. customer_followup  触达结果与采购信号（design D6）
-- 3. sys_config         触达序列天数（design D5）
-- 停止原因码值固定在代码里（FollowupConstants.STOP_REASONS），与客户经营「没有需求的原因」一致，不另建字典。

ALTER TABLE `followup_item`
    MODIFY COLUMN `stage_code` varchar(16) NOT NULL DEFAULT '' COMMENT '阶段编码（D1–D7、S1–S7、F1–F6、A1–A6、G4–G7）',
    MODIFY COLUMN `stage_group` tinyint(2) NOT NULL DEFAULT 1 COMMENT '阶段分组（商机成交：1-报价前、2-报价后、3-PI后；订单履约：4-采购中、5-验收发货、6-在途；售后评价：7-签收、8-使用、9-问题、10-满意评价；客户经营：11-需求挖掘、12-复购机会、13-交叉销售、14-长期合作；主动开发：15-找客户、16-找人触达、17-挖需求）',
    MODIFY COLUMN `close_reason` tinyint(2) NOT NULL DEFAULT 0 COMMENT '移出原因（0-未移出、1-单据结束、2-被新单据接替、3-商机结束、4-单据删除或作废、5-售后结束、6-不再是成交客户、7-转商机、8-停止开发、9-已进入商机成交）',
    ADD COLUMN `prospect_reason` varchar(200) NOT NULL DEFAULT '' COMMENT '主动开发：为什么值得开发' AFTER `dormant_since`,
    ADD COLUMN `prospect_source` tinyint(2) NOT NULL DEFAULT 0 COMMENT '主动开发：从哪里找到的（0-未填、1-Google、2-LinkedIn、3-展会名录、4-海关数据、5-老客户介绍、6-其他）' AFTER `prospect_reason`,
    ADD COLUMN `touch_count` int(11) NOT NULL DEFAULT 0 COMMENT '主动开发：触达次数（已发出触达、客户回复了各计 1 次）' AFTER `prospect_source`,
    ADD COLUMN `first_touch_date` date NULL COMMENT '主动开发：首次触达日，序列第 1 天' AFTER `touch_count`,
    ADD COLUMN `replied_at` datetime NULL COMMENT '主动开发：第一次选「客户回复了」的时间' AFTER `first_touch_date`,
    ADD COLUMN `signal_text` varchar(200) NOT NULL DEFAULT '' COMMENT '主动开发：采购信号（型号、数量、时间），转商机时的需求摘要' AFTER `replied_at`,
    ADD COLUMN `signal_at` datetime NULL COMMENT '主动开发：第一次选「出现采购信号」的时间' AFTER `signal_text`,
    ADD COLUMN `stop_reason` varchar(16) NOT NULL DEFAULT '' COMMENT '主动开发：停止原因（REFUSED-明确拒绝、MISMATCH-明显不匹配、UNREACHABLE-联系不上、NO_REPLY-序列发完仍没回复、OTHER-其他）' AFTER `signal_at`,
    ADD COLUMN `stop_note` varchar(200) NOT NULL DEFAULT '' COMMENT '主动开发：停止说明（原因为其他时必填）' AFTER `stop_reason`;

ALTER TABLE `customer_followup`
    MODIFY COLUMN `situation` int(11) NOT NULL DEFAULT 0 COMMENT '客户情况位掩码（1-给了反馈、2-在谈条件、4-没回复、8-客户有新需求、16-聊了新品类、32-当前没有需求、64-客户回复了、128-出现采购信号、256-明确拒绝）',
    MODIFY COLUMN `channel` tinyint(2) NOT NULL DEFAULT 0 COMMENT '方式（0-无、1-WhatsApp、2-邮件、3-电话、4-其他、5-LinkedIn）',
    ADD COLUMN `touch_result` tinyint(2) NOT NULL DEFAULT 0 COMMENT '主动开发的触达结果（0-无、1-已发出触达、2-客户回复了、3-出现采购信号、4-没回复、5-明确拒绝）' AFTER `due_date`,
    ADD COLUMN `signal_text` varchar(200) NOT NULL DEFAULT '' COMMENT '出现采购信号：型号、数量、时间' AFTER `touch_result`;

INSERT INTO `sys_config` (`tenant_id`, `config_key`, `config_name`, `config_value`, `config_type`, `is_builtin`, `config_group`, `remark`, `create_by`, `update_by`)
SELECT 0, 'sales.followup.develop.sequence-days', '客户跟进：主动开发触达序列天数', '1,4,9,16', 'STRING', 1, 'sales',
       '首次触达日为第 1 天，之后第几天提醒再跟，逗号分隔、从小到大；最后一个节点之后仍没回复，建议转培育或停止开发', 'sys', 'sys'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_config` c WHERE c.`tenant_id` = 0 AND c.`config_key` = 'sales.followup.develop.sequence-days');

-- 回退：
-- DELETE FROM `sys_config` WHERE `tenant_id` = 0 AND `config_key` = 'sales.followup.develop.sequence-days';
-- UPDATE `followup_item` SET `deleted_at` = NOW() WHERE `board` = 1 AND `deleted_at` IS NULL;  -- 先软删除开发记录（已转出的商机保留）
-- ALTER TABLE `customer_followup` DROP COLUMN `signal_text`, DROP COLUMN `touch_result`;
-- ALTER TABLE `followup_item` DROP COLUMN `stop_note`, DROP COLUMN `stop_reason`, DROP COLUMN `signal_at`,
--     DROP COLUMN `signal_text`, DROP COLUMN `replied_at`, DROP COLUMN `first_touch_date`, DROP COLUMN `touch_count`,
--     DROP COLUMN `prospect_source`, DROP COLUMN `prospect_reason`;
-- DELETE FROM `flyway_schema_history` WHERE `version` = '1.5.13';
