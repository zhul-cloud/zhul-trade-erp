-- 登录账号邮箱与用户资料对齐：用户管理改邮箱以前只改了用户资料（user_basic），找回密码按登录账号（account）上的旧邮箱发验证码。
-- 以用户资料为准，资料邮箱非空且与登录账号不同的覆盖到登录账号；原值先存到备份表，供反向 SQL 恢复。

CREATE TABLE IF NOT EXISTS `account_email_bak_v138` (
  `account_id` int(11)      NOT NULL COMMENT '登录账号ID',
  `email`      varchar(100) NOT NULL DEFAULT '' COMMENT '迁移前的登录账号邮箱',
  PRIMARY KEY (`account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V1.3.8 登录账号邮箱对齐前的备份（反向 SQL 用）';

INSERT INTO `account_email_bak_v138` (`account_id`, `email`)
SELECT a.`id`, a.`email`
FROM `account` a
JOIN `user_basic` u ON u.`id` = a.`user_id`
WHERE u.`email` <> '' AND u.`email` <> a.`email`;

UPDATE `account` a
JOIN `user_basic` u ON u.`id` = a.`user_id`
SET a.`email` = u.`email`, a.`update_time` = NOW()
WHERE u.`email` <> '' AND u.`email` <> a.`email`;
