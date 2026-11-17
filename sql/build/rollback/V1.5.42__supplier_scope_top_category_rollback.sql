-- V1.5.42 回退说明：数据迁移，不能自动回退。
-- 细分品类换成一级品类后，原来具体选的是哪几个细分品类已不可知；合并时软删除的重复行可按
--   update_by = 'migration' AND deleted_at IS NOT NULL
-- 找到并清空 deleted_at 恢复，但它们的 category_id 已是一级品类。回退代码后，细分品类校验会拒绝保存这些供应商，需重新选择主营品类。
SELECT 1;
