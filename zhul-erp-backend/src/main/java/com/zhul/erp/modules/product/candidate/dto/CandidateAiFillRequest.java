package com.zhul.erp.modules.product.candidate.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 批量 AI 解析：mode 为 category（品类）或 brand（品牌） */
@Data
public class CandidateAiFillRequest {
    @NotNull(message = "请选择解析品类还是品牌")
    @Pattern(regexp = "category|brand", message = "解析方式只能是品类或品牌")
    private String mode;

    @NotEmpty(message = "请勾选候选")
    @Size(max = 200, message = "每次最多 200 条")
    private List<Long> ids;
}
