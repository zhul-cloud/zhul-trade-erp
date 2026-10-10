package com.zhul.erp.modules.purchase.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 拆分采购需求 */
@Data
public class SplitRequirementRequest {
    @NotNull(message = "请填写拆出数量")
    @Min(value = 1, message = "拆出数量须大于 0")
    private Integer quantity;
    /** 新需求的采购员；为空时沿用原需求 */
    private Long purchaserId;
    /** 新需求的建议供应商；与 channel + shopName（线上店铺）都为空时沿用原需求的建议 */
    private Long supplierId;
    private Integer channel;
    private String shopName;
}
