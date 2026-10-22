-- 登录账号邮箱对齐迁移 V1.3.8 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.3.8 的记录）。

UPDATE `account` a
JOIN `account_email_bak_v138` b ON b.`account_id` = a.`id`
SET a.`email` = b.`email`, a.`update_time` = NOW();

DROP TABLE IF EXISTS `account_email_bak_v138`;
