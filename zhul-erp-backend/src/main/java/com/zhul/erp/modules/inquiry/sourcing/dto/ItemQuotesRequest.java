package com.zhul.erp.modules.inquiry.sourcing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 一个型号本次的询价结果（替换本人尚未提交的草稿） */
@Data
public class ItemQuotesRequest {
    @NotNull(message = "缺少型号")
    private Long itemId;
    private Boolean noStock;
    @Size(max = 300, message = "无货说明不能超过 300 字")
    private String noStockNote;
    @Valid
    @Size(max = 20, message = "每个型号最多 20 条报价")
    private List<QuoteEntryRequest> quotes;
    /** 采购询价时核实的生产状态（字典 inquiry_lifecycle 的码值），为空表示不改 */
    private Integer lifecycle;
    @Size(max = 128, message = "替代型号不能超过 128 字")
    private String replacementModel;
    /** 采购回填的真实型号（询盘原文不是型号或写错时）；不传表示不改，空串表示清空 */
    @jakarta.validation.constraints.Size(max = 128, message = "真实型号不能超过 128 字")
    private String actualModel;
}
