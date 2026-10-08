package com.zhul.erp.modules.quotation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 保存草稿报价单（预览也用同一结构，不落库）：型号行以 id 对应已有行，请求里没有的行删除 */
@Data
public class SaveQuotationRequest {
    @NotBlank(message = "请选择报价币种")
    private String currencyCode;
    @Size(max = 16, message = "贸易术语不能超过 16 个字符")
    private String incoterm;
    @Size(max = 64, message = "术语地点不能超过 64 个字符")
    private String incotermPlace;
    private LocalDate validUntil;
    @Size(max = 500, message = "备注不能超过 500 字")
    private String remark;
    @NotNull(message = "请至少保留一个型号")
    @Size(min = 1, max = 300, message = "型号行需要 1–300 行")
    @Valid
    private List<Item> items;
    @Size(max = 20, message = "费用行最多 20 行")
    @Valid
    private List<Fee> fees = new ArrayList<>();

    @Data
    public static class Item {
        @NotNull(message = "型号行缺少 ID")
        private Long id;
        @Size(max = 300, message = "描述不能超过 300 字")
        private String description;
        private Integer leadTime;
        @Size(max = 32, message = "质保不能超过 32 个字符")
        private String warranty;
        @NotNull(message = "请填写数量")
        @Min(value = 1, message = "数量需要是正整数")
        private Integer quantity;
        /** 定价方式（1-按毛利率、2-按加价、3-直接填外币售价） */
        @NotNull(message = "请选择定价方式")
        private Integer pricingMode;
        private BigDecimal marginRate;
        private BigDecimal markupAmount;
        private BigDecimal unitPrice;
        /** 替代型号（无货行可改、可清空） */
        @Size(max = 128, message = "替代型号不能超过 128 个字符")
        private String replacementModel;
    }

    @Data
    public static class Fee {
        @NotBlank(message = "请填写费用名称")
        @Size(max = 64, message = "费用名称不能超过 64 个字符")
        private String feeName;
        @NotNull(message = "请填写费用金额")
        private BigDecimal amount;
    }
}
