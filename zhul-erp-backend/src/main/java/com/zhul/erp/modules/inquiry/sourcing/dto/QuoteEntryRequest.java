package com.zhul.erp.modules.inquiry.sourcing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** 一条询价结果 */
@Data
public class QuoteEntryRequest {
    @NotNull(message = "请选择渠道")
    @Min(value = 1, message = "渠道不正确")
    @Max(value = 5, message = "渠道不正确")
    private Integer channel;
    @Size(max = 128, message = "店铺名称不能超过 128 字")
    private String shopName;
    /** 渠道为供应商时必填 */
    private Long supplierId;
    @NotNull(message = "请填写单价")
    @DecimalMin(value = "0", message = "单价不能为负")
    @Digits(integer = 16, fraction = 2, message = "单价最多两位小数")
    private BigDecimal unitPrice;
    private Boolean taxIncluded;
    /** 含税时的税率（字典 inquiry_tax_rate 的百分比整数），不传按 13 */
    private Integer taxRate;
    /** 货况码值，见字典 inquiry_item_condition；0 / 空为未填 */
    private Integer itemCondition;
    /** 货期码值，见字典 inquiry_lead_time；0 / 空为未填 */
    private Integer leadTime;
    @Size(max = 300, message = "备注不能超过 300 字")
    private String note;
    /** 是否为推荐报价；同一型号最多一条，都不标时按全新原装最低价自动推荐 */
    private Boolean recommended;
}
