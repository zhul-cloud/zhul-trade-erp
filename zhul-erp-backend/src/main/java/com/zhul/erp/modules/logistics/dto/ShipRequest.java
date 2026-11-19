package com.zhul.erp.modules.logistics.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 登记出运 / 修改运单。承运商、运单号、运费是否必填由服务层按出运单是否交客户指定货代判断
 * （openspec feedback-1119：交客户指定货代时只要求交货日期）。
 */
@Data
public class ShipRequest {
    @Size(max = 32, message = "承运商不能超过32个字")
    private String carrier;
    @Size(max = 64, message = "运单号不能超过64个字")
    private String waybillNo;
    private LocalDate shippedDate;
    @DecimalMin(value = "0", message = "运费不能为负数")
    @Digits(integer = 16, fraction = 2, message = "运费最多两位小数")
    private BigDecimal freight;
    /** 面单（PDF 或图片，先上传附件拿到 ID） */
    private List<Long> attachmentIds;
}
