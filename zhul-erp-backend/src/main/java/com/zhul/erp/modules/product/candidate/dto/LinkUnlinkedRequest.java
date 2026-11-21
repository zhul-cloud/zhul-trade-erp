package com.zhul.erp.modules.product.candidate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 待关联清单：为一组明细选择商品，或填写真实型号 */
@Data
public class LinkUnlinkedRequest {
    /** 归并键，取自清单 */
    @NotBlank(message = "请选择要关联的型号")
    @Size(max = 300, message = "参数错误")
    private String key;
    /** 选择的商品（与真实型号二选一） */
    private Long productId;
    /** 真实型号的品牌 */
    @Size(max = 64, message = "品牌不能超过 64 个字")
    private String brand;
    /** 真实型号 */
    @Size(max = 128, message = "型号不能超过 128 个字")
    private String model;
}
