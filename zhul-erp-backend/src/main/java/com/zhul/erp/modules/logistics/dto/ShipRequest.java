package com.zhul.erp.modules.logistics.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 登记出运 / 修改运单 */
@Data
public class ShipRequest {
    @NotBlank(message = "请填写承运商")
    @Size(max = 32, message = "承运商不能超过32个字")
    private String carrier;
    @NotBlank(message = "请填写运单号")
    @Size(max = 64, message = "运单号不能超过64个字")
    private String waybillNo;
    @NotNull(message = "请填写出运日期")
    private LocalDate shippedDate;
    @NotNull(message = "请填写实际运费")
    @DecimalMin(value = "0", message = "运费不能为负数")
    @Digits(integer = 16, fraction = 2, message = "运费最多两位小数")
    private BigDecimal freight;
    /** 面单（PDF 或图片，先上传附件拿到 ID） */
    private List<Long> attachmentIds;
}
