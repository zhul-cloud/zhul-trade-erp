package com.zhul.erp.modules.product.candidate.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 批量通过：按候选现有的品牌与建议品类建档 */
@Data
public class BatchApproveRequest {
    @NotEmpty(message = "请勾选候选")
    @Size(max = 100, message = "一次最多 100 个")
    private List<Long> ids;
}
