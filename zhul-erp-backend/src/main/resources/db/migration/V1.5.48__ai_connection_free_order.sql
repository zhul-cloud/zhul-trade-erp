-- V1.5.48：AI 连接方式不再分组，一个列表统一排序（openspec ai-connection-free-order）
-- 以前 sort_order 按组各自编号（API 组 1、2，订阅组 1、2），没保存过的卡片取 10 + 次序，API 组整体在前。
-- 现在整个列表统一编号；没保存过的卡片 API 取 10 + 次序、订阅取 30 + 次序。
-- 订阅行平移 20 后：已保存 API（1、2）< 未保存 API（10、11）< 已保存订阅（21～23）< 未保存订阅（30、31），
-- 与升级前「API 组在前、组内按原顺序」完全一致；公司自己调整并保存后才按新顺序。

UPDATE `sys_ai_provider_config`
SET `sort_order` = `sort_order` + 20
WHERE `mode` = 'subscription';

-- 回退：UPDATE `sys_ai_provider_config` SET `sort_order` = `sort_order` - 20 WHERE `mode` = 'subscription' AND `sort_order` > 20;
