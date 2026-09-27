-- V1.2.2：加宽 username 列，支撑邮箱作为登录账号
-- 租户管理模块（见 docs/03-产品原型/00-用户域/02-租户管理/00-租户管理/）新增租户时，
-- 联系人邮箱即管理员登录账号（PRD 2.2 节本身就把 sys_user.username 定义为 VARCHAR(100)），
-- 但实际建表时 user_basic / account / account_local_auth 三张表的 username 列都是
-- varchar(20)，邮箱地址普遍超过 20 字符，插入时报 "Data too long for column 'username'"。
-- 这三张表在 V1__init.sql 里创建，不能回头改那个文件，只能在这里补一版加宽。

-- MySQL 8 的 MODIFY COLUMN 不支持 IF 判断，用 information_schema 判断当前长度后再执行，
-- 保证可重复执行（虽然 Flyway 只会真的跑一次，这里仍按项目既有习惯写成幂等的）
SET @ddl = (SELECT IF(
    (SELECT character_maximum_length FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'user_basic' AND column_name = 'username') < 100,
    'ALTER TABLE `user_basic` MODIFY COLUMN `username` varchar(100) NOT NULL DEFAULT '''' COMMENT ''用户名''',
    'SELECT 1'));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(
    (SELECT character_maximum_length FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'account' AND column_name = 'username') < 100,
    'ALTER TABLE `account` MODIFY COLUMN `username` varchar(100) NOT NULL DEFAULT '''' COMMENT ''用户名''',
    'SELECT 1'));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(
    (SELECT character_maximum_length FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'account_local_auth' AND column_name = 'username') < 100,
    'ALTER TABLE `account_local_auth` MODIFY COLUMN `username` varchar(100) NOT NULL DEFAULT '''' COMMENT ''用户名（冗余）''',
    'SELECT 1'));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================
-- 回滚（需要时手工执行；仅在确认没有超过 20 字符的 username 时才能回滚，否则会截断报错）
-- ============================
-- ALTER TABLE `user_basic` MODIFY COLUMN `username` varchar(20) NOT NULL DEFAULT '' COMMENT '用户名';
-- ALTER TABLE `account` MODIFY COLUMN `username` varchar(20) NOT NULL DEFAULT '' COMMENT '用户名';
-- ALTER TABLE `account_local_auth` MODIFY COLUMN `username` varchar(20) NOT NULL DEFAULT '' COMMENT '用户名（冗余）';
