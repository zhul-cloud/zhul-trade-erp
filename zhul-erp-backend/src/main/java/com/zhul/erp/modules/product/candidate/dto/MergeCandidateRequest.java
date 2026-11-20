package com.zhul.erp.modules.product.candidate.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 并入已有商品 */
@Data
public class MergeCandidateRequest {
    @NotNull(message = "请选择要并入的商品")
    private Long productId;

    /** 并入原因，可空 */
    @jakarta.validation.constraints.Size(max = 180, message = "原因不能超过 180 个字")
    private String reason;
}
