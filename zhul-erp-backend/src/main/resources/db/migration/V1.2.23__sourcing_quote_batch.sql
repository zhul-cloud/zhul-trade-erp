-- ===========================
-- 询价记录增加「提交批次」：同一次保存 / 提交写入的记录同一批次，用于分配工作台「修改记录」按版本展示
-- 旧数据为空，展示时按询价人 + 提交时间（精确到秒）分组
-- ===========================
ALTER TABLE `sourcing_quote`
    ADD COLUMN `submit_batch` varchar(36) NOT NULL DEFAULT '' COMMENT '提交批次（同一次写入的记录相同，用于按版本展示修改记录）' AFTER `import_id`;
