package com.zhul.erp.modules.product.candidate.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 批量修改品牌、品类：没填的那一项保持不变，两项至少填一项 */
@Data
public class BatchEditRequest {
    @NotEmpty(message = "请勾选候选")
    @Size(max = 200, message = "每次最多 200 条")
    private List<Long> ids;
    private Long brandId;
    private Long categoryId;
}
