package com.zhul.erp.modules.purchase.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 需求池生成采购单：每组一家供应商 */
@Data
public class GeneratePurchaseRequest {
    @Valid
    @NotEmpty(message = "请选择需求")
    private List<Group> groups;

    @Data
    public static class Group {
        @NotNull(message = "请为每组选定供应商")
        private Long supplierId;
        @NotEmpty(message = "请选择需求")
        private List<Long> requirementIds;
    }
}
