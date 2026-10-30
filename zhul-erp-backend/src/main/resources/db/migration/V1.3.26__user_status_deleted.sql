-- V1.3.26：删除用户改为软删除，状态加一个取值 2-已删除（此前删除只是把状态改成 0，和停用没有区别）。
-- 只改字段注释，不动数据：此前被「删除」的用户仍是停用（0），需要的话在用户管理里再删一次。
ALTER TABLE `user_basic` MODIFY `status` tinyint(2) NOT NULL DEFAULT 1 COMMENT '状态（0-禁用、1-启用、2-已删除）';
ALTER TABLE `account` MODIFY `status` tinyint(2) NOT NULL DEFAULT 1 COMMENT '状态（0-禁用、1-启用、2-已删除）';
