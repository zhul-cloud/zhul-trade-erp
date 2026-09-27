package com.zhul.erp.modules.masterdata.dto;

import com.zhul.erp.modules.product.dto.SaveBrandRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 确认待确认品牌：设为已有品牌的别名时传 brandId；新建为品牌时传 brand（名称为空则用待确认名称）。
 */
@Data
public class ConfirmPendingBrandRequest {
    @NotBlank(message = "待确认品牌不能为空")
    private String pendingKey;
    private Long brandId;
    private SaveBrandRequest brand;
}
