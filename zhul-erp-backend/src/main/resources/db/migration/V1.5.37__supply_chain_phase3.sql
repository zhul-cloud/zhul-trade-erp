-- 供应链第三期 报价与履约协同（OpenSpec supply-chain-phase3-quote-fulfillment）：只更新字段注释，增加新的取值说明。
ALTER TABLE `sc_followup_log`
    MODIFY COLUMN `source_type` tinyint(2) NOT NULL DEFAULT 0 COMMENT '系统登记来源（0-人工、1-询价回价、2-询价无货、3-撤回回价、4-系统提醒、5-报价发送确认、6-采购延期、7-采购取消、8-到货差异）',
    MODIFY COLUMN `source_id` bigint NULL COMMENT '来源ID（询价记录ID、报价行ID、采购行ID、到货差异ID）；人工处理为 NULL',
    MODIFY COLUMN `source_doc_no` varchar(32) NOT NULL DEFAULT '' COMMENT '来源单号（询价任务编号、报价单号、采购单号）';

ALTER TABLE `sc_demand`
    MODIFY COLUMN `reminder_type` tinyint(2) NOT NULL DEFAULT 0 COMMENT '系统提醒类型（0-无、1-询价无货、2-证据过期、3-新进入矩阵待确认、4-没有下一步、5-履约异常）';
