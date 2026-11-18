package com.zhul.erp.modules.aitask.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * AI 设置页底部保存条一次保存的全部修改：连接方式的顺序与开关，以及改过的各连接方式设置。
 * order 为整个列表的顺序，API Key 与平台订阅可以互相穿插（openspec ai-connection-free-order）。
 */
@Data
public class SaveAiSettingsRequest {

    @NotNull(message = "请提供连接方式的顺序")
    @Size(max = 8, message = "连接方式过多")
    @Valid
    private List<Item> order;

    /** 改过设置的连接方式；没改的不传 */
    @Size(max = 8, message = "连接方式过多")
    @Valid
    private List<SaveAiConfigRequest> profiles;

    @Data
    public static class Item {
        @NotNull(message = "请提供用法")
        @Pattern(regexp = "^(api|subscription)$", message = "运行方式只能是 api 或 subscription")
        private String mode;

        @NotNull(message = "请提供服务商")
        @Pattern(regexp = "^(claude|openai)$", message = "服务商只能是 claude 或 openai")
        private String provider;

        private boolean enabled = true;
    }
}
