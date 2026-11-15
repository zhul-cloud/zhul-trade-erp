-- 供应链第三期 V1.5.37 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.5.37 的记录）。
-- 只恢复字段注释；已写入的新取值（来源 5–8、提醒类型 5）数据保留，旧版本程序按未知取值显示。
ALTER TABLE `sc_followup_log`
    MODIFY COLUMN `source_type` tinyint(2) NOT NULL DEFAULT 0 COMMENT '系统登记来源（0-人工、1-询价回价、2-询价无货、3-撤回回价、4-系统提醒）',
    MODIFY COLUMN `source_id` bigint NULL COMMENT '系统登记的来源ID（询价记录ID）；人工处理为 NULL',
    MODIFY COLUMN `source_doc_no` varchar(32) NOT NULL DEFAULT '' COMMENT '来源单号（询价任务编号）';

ALTER TABLE `sc_demand`
    MODIFY COLUMN `reminder_type` tinyint(2) NOT NULL DEFAULT 0 COMMENT '系统提醒类型（0-无、1-询价无货、2-证据过期、3-新进入矩阵待确认、4-没有下一步）';
