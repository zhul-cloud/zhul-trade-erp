-- V1.3.21：AI 设置按连接方式分别保存（openspec ai-provider-profiles-and-subscription-access）。
-- 每个租户 × 用法（api / subscription）× 服务商一行；sys_ai_config 只保留当前选择（mode、provider）。
CREATE TABLE `sys_ai_provider_config`
(
    `id`                int(11)      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`         int(11)      NOT NULL DEFAULT 0  COMMENT '租户ID（0-平台租户）；订阅设置只有平台租户的行有效',
    `mode`              varchar(16)  NOT NULL DEFAULT 'api' COMMENT '用法（api-本公司API Key、subscription-平台订阅）',
    `provider`          varchar(16)  NOT NULL DEFAULT 'claude' COMMENT '服务商（claude、openai）',
    `api_key`           varchar(512) NOT NULL DEFAULT '' COMMENT 'API Key，AES-GCM密文（v2:前缀），空表示未填写；订阅始终为空；任何接口只返回脱敏值',
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
    UNIQUE KEY `uk_tenant_mode_provider` (`tenant_id`, `mode`, `provider`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB AUTO_INCREMENT = 1000 DEFAULT CHARSET = utf8mb4 COMMENT = 'AI连接方式设置表（每个租户每种用法每个服务商一行）';

-- 旧配置原样迁入：密文直接复制不解密；订阅不使用 Key
INSERT INTO `sys_ai_provider_config` (`tenant_id`, `mode`, `provider`, `api_key`, `parse_model`, `translate_model`,
                                      `parse_effort`, `translate_effort`, `max_web_searches`, `status`,
                                      `create_time`, `create_by`, `update_time`, `update_by`)
SELECT `tenant_id`, `mode`, `provider`, IF(`mode` = 'subscription', '', `api_key`), `parse_model`, `translate_model`,
       `parse_effort`, `translate_effort`, `max_web_searches`, `status`,
       `create_time`, `create_by`, `update_time`, `update_by`
FROM `sys_ai_config`;

-- 已切到订阅、但旧行里还留着之前填的 Key：同时保存为该服务商的本公司 API Key，不丢失
INSERT INTO `sys_ai_provider_config` (`tenant_id`, `mode`, `provider`, `api_key`, `status`,
                                      `create_time`, `create_by`, `update_time`, `update_by`)
SELECT `tenant_id`, 'api', `provider`, `api_key`, `status`, `create_time`, `create_by`, `update_time`, `update_by`
FROM `sys_ai_config`
WHERE `mode` = 'subscription' AND `api_key` <> '';

ALTER TABLE `sys_ai_config`
    COMMENT = '租户当前使用的AI连接方式（每个租户一行）；各连接方式的设置见 sys_ai_provider_config',
    MODIFY `mode`             varchar(16)  NOT NULL DEFAULT 'api' COMMENT '当前用法（api-本公司API Key、subscription-平台订阅）',
    MODIFY `provider`         varchar(16)  NOT NULL DEFAULT 'claude' COMMENT '当前服务商（claude、openai）',
    MODIFY `api_key`          varchar(512) NOT NULL DEFAULT '' COMMENT '已弃用（V1.3.21 迁入 sys_ai_provider_config，不再读写）',
    MODIFY `parse_model`      varchar(100) NOT NULL DEFAULT '' COMMENT '已弃用（V1.3.21 迁入 sys_ai_provider_config，不再读写）',
    MODIFY `translate_model`  varchar(100) NOT NULL DEFAULT '' COMMENT '已弃用（V1.3.21 迁入 sys_ai_provider_config，不再读写）',
    MODIFY `parse_effort`     varchar(16)  NOT NULL DEFAULT '' COMMENT '已弃用（V1.3.21 迁入 sys_ai_provider_config，不再读写）',
    MODIFY `translate_effort` varchar(16)  NOT NULL DEFAULT '' COMMENT '已弃用（V1.3.21 迁入 sys_ai_provider_config，不再读写）',
    MODIFY `max_web_searches` int(11)      NOT NULL DEFAULT 20 COMMENT '已弃用（V1.3.21 迁入 sys_ai_provider_config，不再读写）';
