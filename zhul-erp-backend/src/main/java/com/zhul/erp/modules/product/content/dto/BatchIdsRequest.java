package com.zhul.erp.modules.product.content.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 批量操作的 ID：批量确认 AI 结果为 AI 生成记录 ID，批量资料已核为商品 ID */
@Data
public class BatchIdsRequest {
    @NotEmpty(message = "请勾选要处理的商品")
    @Size(max = 200, message = "一次最多处理 200 个")
    private List<Long> ids;
}
