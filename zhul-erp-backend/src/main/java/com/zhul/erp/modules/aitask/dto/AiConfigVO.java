package com.zhul.erp.modules.aitask.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** 当前租户的 AI 配置：按使用顺序排好的连接方式卡片（API Key 与平台订阅在一个列表里） */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiConfigVO {
    /** 是否平台租户 */
    private boolean platform;
    /** 能否使用平台订阅（平台始终可以；其他租户看套餐授权）；不能时 connections 里没有订阅卡片 */
    private boolean subscriptionAllowed;
    private List<AiConnectionVO> connections;
    /** 各服务商的默认模型：provider → {parse, translate} */
    private Map<String, Map<String, String>> defaultModels;
}
