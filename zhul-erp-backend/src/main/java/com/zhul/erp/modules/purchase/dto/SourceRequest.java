package com.zhul.erp.modules.purchase.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 选定采购渠道：老供应商（supplierId），或线上店铺（channel + shopName） */
@Data
public class SourceRequest {
    @NotEmpty(message = "请选择型号")
    @Size(max = 200, message = "一次最多 200 条")
    private List<Long> ids;
    private Long supplierId;
    /** 1-淘宝、2-1688、3-闲鱼、5-其他 */
    private Integer channel;
    private String shopName;
}
