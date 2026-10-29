package com.zhul.erp.modules.aitask.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/** 当前租户的 AI 配置；Key 只返回是否已填写与脱敏值 */
@Data
public class AiConfigVO {
    /** 是否已保存过配置 */
    private boolean configured;
    private String mode;
    private String provider;
    private boolean hasKey;
    private String apiKeyMasked;
    private String parseModel;
    private String translateModel;
    private String parseEffort;
    private String translateEffort;
    private Integer maxWebSearches;
    /** 当前租户能否选择订阅方式（只有平台租户能） */
    private boolean subscriptionAllowed;
    /** 各服务商的默认模型，供界面占位提示：provider → {parse, translate} */
    private Map<String, Map<String, String>> defaultModels;
    /** 最后修改时间与修改人，未配置时为空 */
    private LocalDateTime updateTime;
    private String updateBy;
}
