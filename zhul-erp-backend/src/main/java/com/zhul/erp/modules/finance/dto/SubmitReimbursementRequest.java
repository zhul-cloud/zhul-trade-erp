package com.zhul.erp.modules.finance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 提交报销单：勾选的待报销项 */
@Data
public class SubmitReimbursementRequest {
    @NotEmpty(message = "请选择要报销的项")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        @NotNull
        private Integer sourceType;
        @NotNull
        private Long sourceId;
    }
}
