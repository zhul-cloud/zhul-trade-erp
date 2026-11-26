package com.zhul.erp.modules.product.candidate.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 待关联清单：勾选的若干组（归并键） */
@Data
public class UnlinkedKeysRequest {
    @NotEmpty(message = "请选择型号")
    @Size(max = 200, message = "每次最多 200 组")
    private List<@Size(max = 300, message = "参数错误") String> keys;
}
