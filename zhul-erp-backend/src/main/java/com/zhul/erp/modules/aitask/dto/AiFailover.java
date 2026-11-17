package com.zhul.erp.modules.aitask.dto;

import lombok.Data;

/**
 * 一次自动切换：原连接方式（用法:服务商，如 api:claude）、原因分类与发生时间（秒级时间戳）。
 * 原因只用分类码，不含服务商返回的原文，避免带出 Key 或账号信息（openspec ai-connection-failover）。
 */
@Data
public class AiFailover {
    private String connection;
    /** auth / quota / rate_limit / provider_error / timeout / not_logged_in / usage_limit */
    private String reason;
    /** 发生时间，Unix 秒 */
    private Long at;
}
