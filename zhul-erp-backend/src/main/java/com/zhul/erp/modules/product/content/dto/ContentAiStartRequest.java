package com.zhul.erp.modules.product.content.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 发起 AI 生成：一个或多个商品 */
@Data
public class ContentAiStartRequest {
    @NotEmpty(message = "请选择商品")
    @Size(max = 20, message = "一次最多 AI 生成 20 个商品")
    private List<Long> productIds;
}
