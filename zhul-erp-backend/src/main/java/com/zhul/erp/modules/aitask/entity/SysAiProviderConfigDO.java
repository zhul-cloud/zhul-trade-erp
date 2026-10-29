package com.zhul.erp.modules.aitask.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhul.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 一种 AI 连接方式（租户 × 用法 × 服务商）的设置；apiKey 为 AES-GCM 密文，订阅始终为空 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = "apiKey")
@TableName("sys_ai_provider_config")
public class SysAiProviderConfigDO extends BaseEntity {
    private Integer tenantId;
    private String mode;
    private String provider;
    private String apiKey;
    private String parseModel;
    private String translateModel;
    private String parseEffort;
    private String translateEffort;
    private Integer maxWebSearches;
    private Integer status;
}
