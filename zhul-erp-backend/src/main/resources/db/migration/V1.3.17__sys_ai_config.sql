-- V1.3.17：租户 AI 编排配置，每个租户一行，不继承平台配置。
-- 见 openspec/changes/ai-config-per-tenant/。
CREATE TABLE `sys_ai_config`
(
    `id`                int(11)      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`         int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID（0-平台租户），每个租户一行',
    `mode`              varchar(16)  NOT NULL DEFAULT 'api' COMMENT '运行方式（api-服务商API、subscription-部署者个人订阅，仅平台租户可选）',
    `provider`          varchar(16)  NOT NULL DEFAULT 'claude' COMMENT '服务商（claude、openai）',
    `api_key`           varchar(512) NOT NULL DEFAULT '' COMMENT 'API Key，AES-GCM密文（v2:前缀），空表示未填写；任何接口只返回脱敏值',
    `parse_model`       varchar(100) NOT NULL DEFAULT '' COMMENT '询盘解析模型，空表示用服务商默认值',
    `translate_model`   varchar(100) NOT NULL DEFAULT '' COMMENT '翻译模型，空表示用服务商默认值',
    `parse_effort`      varchar(16)  NOT NULL DEFAULT '' COMMENT '解析思考强度（low、medium、high、xhigh、max），空表示medium',
    `translate_effort`  varchar(16)  NOT NULL DEFAULT '' COMMENT '翻译思考强度（low、medium、high、xhigh、max），空表示medium',
    `max_web_searches`  int(11)      NOT NULL DEFAULT 20 COMMENT '单次解析最大联网搜索次数（1-100）',
    `status`            tinyint(2)   NOT NULL DEFAULT 1  COMMENT '状态（0-禁用、1-启用）',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by`         varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '创建人',
    `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_by`         varchar(32)  NOT NULL DEFAULT 'sys' COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_id` (`tenant_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB AUTO_INCREMENT = 1000 DEFAULT CHARSET = utf8mb4 COMMENT = '租户AI编排配置表';
