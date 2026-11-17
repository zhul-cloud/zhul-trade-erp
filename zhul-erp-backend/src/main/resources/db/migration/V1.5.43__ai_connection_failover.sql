-- V1.5.43：AI 连接方式按顺序自动切换（openspec ai-connection-failover）
-- 不再单选「当前使用的连接方式」：已配置的方式都参与，API Key 组在前、平台订阅组在后，组内按 sort_order 使用；
-- sys_ai_provider_config.status 作为每张卡片的启用开关（原来恒为 1、没有读取）。
-- sys_ai_config.mode / provider 保留不删，新代码不再读取；回退到旧版本时旧代码仍按原单选工作。

ALTER TABLE `sys_ai_provider_config`
    COMMENT = 'AI连接方式设置表（每个租户每种用法每个服务商一行；租户的订阅行只记顺序与开关，模型设置读平台的行）',
    ADD COLUMN `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '组内使用顺序，同一用法内从小到大（API Key 组整体在订阅组之前）' AFTER `provider`,
    MODIFY `status` tinyint(2) NOT NULL DEFAULT 1 COMMENT '是否参与使用（0-关闭、1-启用）';

ALTER TABLE `ai_task`
    ADD COLUMN `used_connection` varchar(32) NOT NULL DEFAULT '' COMMENT '实际完成调用的连接方式（用法:服务商，如 api:openai），空表示未记录' AFTER `model`,
    ADD COLUMN `failovers` json NULL COMMENT '执行中自动切换记录 [{connection, reason, at}]，没有切换为 NULL' AFTER `used_connection`;

-- 1. 组内顺序：原来选中的方式排所在组第一，组内其余按 Claude、OpenAI
UPDATE `sys_ai_provider_config` p
    LEFT JOIN `sys_ai_config` c ON c.`tenant_id` = p.`tenant_id`
SET p.`sort_order` = CASE
                         WHEN c.`mode` = p.`mode` AND c.`provider` = p.`provider` THEN 1
                         WHEN p.`provider` = 'claude' THEN 2
                         ELSE 3 END;

-- 2. 原来选用订阅的租户（含平台）：关闭其 API 卡片，上线后不突然改用付费 Key；需要时自行打开
UPDATE `sys_ai_provider_config` p
    JOIN `sys_ai_config` c ON c.`tenant_id` = p.`tenant_id`
SET p.`status` = 0
WHERE c.`mode` = 'subscription' AND p.`mode` = 'api';

-- 3. 选用订阅的非平台租户：插入订阅行记录顺序（所选服务商排第一）；没有行的订阅按默认顺序、默认启用
INSERT INTO `sys_ai_provider_config` (`tenant_id`, `mode`, `provider`, `sort_order`, `api_key`, `status`,
                                      `create_time`, `create_by`, `update_time`, `update_by`)
SELECT c.`tenant_id`, 'subscription', c.`provider`, 1, '', 1, NOW(), 'sys', NOW(), 'sys'
FROM `sys_ai_config` c
WHERE c.`mode` = 'subscription' AND c.`tenant_id` <> 0
  AND NOT EXISTS (SELECT 1 FROM `sys_ai_provider_config` x
                  WHERE x.`tenant_id` = c.`tenant_id` AND x.`mode` = 'subscription' AND x.`provider` = c.`provider`);

-- 回退（旧版本代码不读取 sort_order、status、租户订阅行与 ai_task 新列，回退后按 sys_ai_config 的单选工作，
-- 第 3 步插入的租户订阅行旧代码不读取，保留即可）：
-- ALTER TABLE `sys_ai_provider_config` DROP COLUMN `sort_order`;
-- UPDATE `sys_ai_provider_config` SET `status` = 1;
-- ALTER TABLE `ai_task` DROP COLUMN `used_connection`, DROP COLUMN `failovers`;
