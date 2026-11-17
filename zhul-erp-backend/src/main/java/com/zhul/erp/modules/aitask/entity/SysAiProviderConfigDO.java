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
    /** 组内使用顺序，同一用法内从小到大（API Key 组整体在订阅组之前） */
    private Integer sortOrder;
    private String apiKey;
    private String parseModel;
    private String translateModel;
    private String parseEffort;
    private String translateEffort;
    private Integer maxWebSearches;
    /** 是否参与使用（0-关闭、1-启用）：每张连接方式卡片的启用开关 */
    private Integer status;
}
