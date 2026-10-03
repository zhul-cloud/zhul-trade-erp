package com.zhul.erp.modules.inquiry.sourcing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** 确认入库的一行（预览中修正后的值） */
@Data
public class ImportConfirmRowRequest {
    @NotNull(message = "缺少型号")
    private Long itemId;
    @NotNull(message = "缺少渠道")
    private Integer channel;
    @Size(max = 128, message = "店铺名称不能超过 128 字")
    private String shopName;
    private Boolean noStock;
    @DecimalMin(value = "0", message = "单价不能为负")
    @Digits(integer = 16, fraction = 2, message = "单价最多两位小数")
    private BigDecimal unitPrice;
    private Boolean taxIncluded;
    private Integer taxRate;
    private Integer itemCondition;
    private Integer leadTime;
    @Size(max = 300, message = "备注不能超过 300 字")
    private String note;
}
