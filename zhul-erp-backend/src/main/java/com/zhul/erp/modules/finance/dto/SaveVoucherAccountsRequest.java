package com.zhul.erp.modules.finance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 保存科目设置：只传要改的业务项 */
@Data
public class SaveVoucherAccountsRequest {
    @NotNull
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        @NotBlank
        private String itemKey;
        @Size(max = 32)
        @Pattern(regexp = "^[0-9A-Za-z.\\-]*$", message = "科目编码只能是字母、数字、点或横线")
        private String accountCode;
        @Size(max = 64)
        private String accountName;
    }
}
