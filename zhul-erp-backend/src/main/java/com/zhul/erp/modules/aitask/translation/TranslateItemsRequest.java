package com.zhul.erp.modules.aitask.translation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 生成英文描述：把中文型号描述翻译成英文 */
@Data
public class TranslateItemsRequest {
    @NotEmpty(message = "没有需要翻译的描述")
    @Size(max = 300, message = "一次最多翻译 300 个型号")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        /** 调用方自己的行标识（如报价行 ID），结果按它对应回去 */
        @NotBlank(message = "缺少行标识")
        @Size(max = 64, message = "行标识过长")
        private String key;
        @NotBlank(message = "描述不能为空")
        @Size(max = 300, message = "描述不能超过 300 字")
        private String text;
        /** 来源询盘型号：翻译成功后补到它的英文描述（已有英文描述的不覆盖） */
        private Long inquiryItemId;
    }
}
