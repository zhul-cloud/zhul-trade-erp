package com.zhul.erp.modules.aitask.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Map;

/** 平台订阅状态：租户只拿到可用与否；登录状态与登录命令只返回给平台 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiSubscriptionStatusVO {
    /** provider → 是否可用；状态查询失败时为 null（未知） */
    private Map<String, Boolean> available;
    /** 仅平台：provider → logged_in / logged_out / unknown */
    private Map<String, String> states;
    /** 仅平台：provider → 容器内登录命令 */
    private Map<String, String> loginCommands;
}
