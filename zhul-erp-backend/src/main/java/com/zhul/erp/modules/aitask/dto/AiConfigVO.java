package com.zhul.erp.modules.aitask.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/** 当前租户的 AI 配置：当前选择 + 各连接方式的设置 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiConfigVO {
    /** 是否已保存过当前选择 */
    private boolean configured;
    /** 当前选择的用法（api / subscription）与服务商；未配置时为默认 api + claude */
    private String mode;
    private String provider;
    /** 是否平台租户 */
    private boolean platform;
    /** 能否使用平台订阅（平台始终可以；其他租户看套餐授权） */
    private boolean subscriptionAllowed;
    /** 本公司 API Key 设置：provider → 设置 */
    private Map<String, AiProfileVO> apiProfiles;
    /** 平台订阅设置：provider → 设置；只在能使用平台订阅时返回 */
    private Map<String, AiProfileVO> subscriptionProfiles;
    /** 各服务商的默认模型：provider → {parse, translate} */
    private Map<String, Map<String, String>> defaultModels;
    /** 当前选择的最后修改时间与修改人，未配置时为空 */
    private LocalDateTime updateTime;
    private String updateBy;
}
