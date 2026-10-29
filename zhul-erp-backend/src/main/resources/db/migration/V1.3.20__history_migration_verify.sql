-- 飞书历史数据迁移（openspec migrate-feishu-history）：批次上保存与旧报表的核对结果，迁移页面直接查看。
ALTER TABLE `migration_batch`
  ADD COLUMN `verify_result` mediumtext NULL COMMENT '与旧报表的核对结果（JSON：汇总与明细；正式执行成功后生成）' AFTER `stage_stats`;
