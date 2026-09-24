-- V1.0.3：撤销 V1.0.2 里的示例租户种子数据。
-- 只保留平台管理员账号（V1.2.0.2 的 platform），本地联调改成从 0 到 1 走一遍：
-- 用 platform 登录 → 自己在「套餐管理」建套餐 → 自己在「租户管理」新增租户
-- （新增租户时会自动生成该租户的管理员账号，不需要再预置一个 admin 账号）。
-- 仅影响 dev-data location（生产环境本来就不加载这个 location）。
DELETE FROM `account_local_auth` WHERE `account_id` = 10000000;
DELETE FROM `account` WHERE `id` = 10000000;
DELETE FROM `user_basic` WHERE `id` = 1000000000;
DELETE FROM `position` WHERE `id` = 10000;
DELETE FROM `department` WHERE `id` = 10001;
DELETE FROM `tenant` WHERE `id` = 1000;
DELETE FROM `tenant_package` WHERE `id` = 1;
