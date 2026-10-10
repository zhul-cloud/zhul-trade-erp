package com.zhul.erp.modules.purchase.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
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
        /** 老供应商；为空时用 channel + shopName 表示线上店铺 */
        private Long supplierId;
        private Integer channel;
        private String shopName;
        @NotEmpty(message = "请选择需求")
        private List<Long> requirementIds;
    }
}
