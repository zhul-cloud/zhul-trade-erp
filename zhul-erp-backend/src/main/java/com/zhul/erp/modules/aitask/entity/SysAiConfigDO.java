package com.zhul.erp.modules.aitask.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhul.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 租户 AI 编排配置，每个租户一行；apiKey 为 AES-GCM 密文 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = "apiKey")
@TableName("sys_ai_config")
public class SysAiConfigDO extends BaseEntity {
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
