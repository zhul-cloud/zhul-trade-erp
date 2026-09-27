package com.zhul.erp.modules.aitask.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateAiTaskRequest {
    @NotBlank(message = "skillId不能为空")
    private String skillId;
    /** 传给 AI 编排服务的原始输入，原样透传，不在本层解析结构 */
    private Object input;
}
