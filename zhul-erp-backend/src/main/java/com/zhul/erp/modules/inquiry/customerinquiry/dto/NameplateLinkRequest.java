package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/** 改铭牌图片对应的型号；0 为未对应 */
@Data
public class NameplateLinkRequest {
    @NotNull(message = "请选择对应的型号")
    @PositiveOrZero(message = "型号不正确")
    private Long inquiryItemId;
}
