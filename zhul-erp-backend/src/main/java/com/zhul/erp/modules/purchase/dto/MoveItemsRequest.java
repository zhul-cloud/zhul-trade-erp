package com.zhul.erp.modules.purchase.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** 草稿行改到其他采购对象：老供应商（supplierId），或线上店铺（channel + shopName） */
@Data
public class MoveItemsRequest {
    @NotEmpty(message = "请选择型号")
    private List<Long> itemIds;
    private Long supplierId;
    /** 1-淘宝、2-1688、3-闲鱼、5-其他 */
    private Integer channel;
    private String shopName;
}
