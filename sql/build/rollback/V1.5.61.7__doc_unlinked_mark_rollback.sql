-- 待关联单据标记 V1.5.61.7 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.5.61.7 的记录）。
DROP TABLE IF EXISTS `doc_unlinked_mark`;
