-- 租户 AI 编排配置 V1.3.17 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.3.17 的记录）。
-- 删表会丢失各租户填写的 AI 配置（含加密的 API Key）；旧版本从 .env 读取 AI 配置，不使用此表。
DROP TABLE IF EXISTS `sys_ai_config`;
