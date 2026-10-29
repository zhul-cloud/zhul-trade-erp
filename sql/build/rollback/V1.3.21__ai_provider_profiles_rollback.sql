-- AI 连接方式设置 V1.3.21 的反向 SQL（备用，只在需要回退该版本时手工执行，执行后还需从 flyway_schema_history 删除 1.3.21 的记录）。
-- sys_ai_config 的旧列在 V1.3.21 后不再写入：回退后旧版本读到的是迁移时的配置，此后在新版本里做的修改（如新增的 OpenAI Key）会丢失。
DROP TABLE IF EXISTS `sys_ai_provider_config`;
ALTER TABLE `sys_ai_config`
    COMMENT = '租户AI编排配置表',
    MODIFY `mode`             varchar(16)  NOT NULL DEFAULT 'api' COMMENT '运行方式（api-服务商API、subscription-部署者个人订阅，仅平台租户可选）',
    MODIFY `provider`         varchar(16)  NOT NULL DEFAULT 'claude' COMMENT '服务商（claude、openai）',
    MODIFY `api_key`          varchar(512) NOT NULL DEFAULT '' COMMENT 'API Key，AES-GCM密文（v2:前缀），空表示未填写；任何接口只返回脱敏值',
    MODIFY `parse_model`      varchar(100) NOT NULL DEFAULT '' COMMENT '询盘解析模型，空表示用服务商默认值',
    MODIFY `translate_model`  varchar(100) NOT NULL DEFAULT '' COMMENT '翻译模型，空表示用服务商默认值',
    MODIFY `parse_effort`     varchar(16)  NOT NULL DEFAULT '' COMMENT '解析思考强度（low、medium、high、xhigh、max），空表示medium',
    MODIFY `translate_effort` varchar(16)  NOT NULL DEFAULT '' COMMENT '翻译思考强度（low、medium、high、xhigh、max），空表示medium',
    MODIFY `max_web_searches` int(11)      NOT NULL DEFAULT 20 COMMENT '单次解析最大联网搜索次数（1-100）';
