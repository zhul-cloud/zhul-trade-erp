package com.zhul.erp.modules.aitask.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/** 保存、测试连接、获取模型列表共用的 AI 配置入参；apiKey 留空表示沿用已保存的 Key */
@Data
@ToString(exclude = "apiKey")
public class SaveAiConfigRequest {

    @Pattern(regexp = "^(api|subscription)?$", message = "运行方式只能是 api 或 subscription")
    private String mode;

    @Pattern(regexp = "^(claude|openai)?$", message = "服务商只能是 claude 或 openai")
    private String provider;

    @Size(max = 300, message = "API Key 过长")
    private String apiKey;

    @Size(max = 100, message = "解析模型名称过长")
    private String parseModel;

    @Size(max = 100, message = "翻译模型名称过长")
    private String translateModel;

    @Pattern(regexp = "^(low|medium|high|xhigh|max)?$", message = "思考强度只能是 low、medium、high、xhigh、max")
    private String parseEffort;

    @Pattern(regexp = "^(low|medium|high|xhigh|max)?$", message = "思考强度只能是 low、medium、high、xhigh、max")
    private String translateEffort;

    @NotNull(message = "请填写最大搜索次数")
    @Min(value = 1, message = "最大搜索次数为 1 到 100")
    @Max(value = 100, message = "最大搜索次数为 1 到 100")
    private Integer maxWebSearches;
}
