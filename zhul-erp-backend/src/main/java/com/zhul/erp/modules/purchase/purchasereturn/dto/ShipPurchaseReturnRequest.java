package com.zhul.erp.modules.purchase.purchasereturn.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 寄出：寄出日期、快递必填，运费选填；填了运费时垫付人默认当前用户，companyPaid 为真时公司支付（不进待报销） */
@Data
public class ShipPurchaseReturnRequest {
    @NotNull(message = "请填写寄出日期")
    private LocalDate shipDate;
    @NotBlank(message = "请填写快递公司")
    @Size(max = 32, message = "快递公司最多 32 个字")
    private String carrier;
    @NotBlank(message = "请填写快递单号")
    @Size(max = 64, message = "快递单号最多 64 个字")
    private String trackingNo;
    @DecimalMin(value = "0", message = "运费不能为负数")
    private BigDecimal freightCny;
    private Long payerId;
    private Boolean companyPaid;
    /** 运费截图（附件 ID，类型 RETURN_PROOF） */
    private List<Long> proofIds;
}
