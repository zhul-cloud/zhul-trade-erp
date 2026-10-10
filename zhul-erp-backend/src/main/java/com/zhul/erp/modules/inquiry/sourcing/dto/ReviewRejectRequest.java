package com.zhul.erp.modules.inquiry.sourcing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 退回兼职修改 */
@Data
public class ReviewRejectRequest {
    @NotEmpty(message = "请选择要退回的型号")
    @Valid
    private List<Item> items;
    @NotBlank(message = "请填写退回原因")
    @Size(max = 200, message = "退回原因不能超过 200 字")
    private String reason;

    @Data
    public static class Item {
        @NotNull(message = "型号不能为空")
        private Long itemId;
        @NotNull(message = "回价人不能为空")
        private Long quotedBy;
    }
}
