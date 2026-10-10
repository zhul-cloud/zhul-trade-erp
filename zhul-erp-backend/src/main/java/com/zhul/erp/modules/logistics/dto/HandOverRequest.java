package com.zhul.erp.modules.logistics.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 交国内快递（一张或多张同一货代的出库单） */
@Data
public class HandOverRequest {
    @NotEmpty(message = "请选择出库单")
    private List<Long> outboundIds;
    @NotBlank(message = "请填写快递公司")
    @Size(max = 32, message = "快递公司不能超过32个字")
    private String carrier;
    @Size(max = 64, message = "快递单号不能超过64个字")
    private String trackingNo;
    @NotNull(message = "请填写发出日期")
    private LocalDate sentDate;
    @NotNull(message = "请填写运费")
    @DecimalMin(value = "0", message = "运费不能为负数")
    @Digits(integer = 16, fraction = 2, message = "运费最多两位小数")
    private BigDecimal freight;
    /** 垫付人，默认当前用户 */
    private Long payerId;
}
