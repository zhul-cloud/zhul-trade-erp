package com.zhul.erp.modules.product.candidate.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 驳回候选：1-不是型号、2-型号错误、3-其他（须说明） */
@Data
public class RejectCandidateRequest {
    @NotNull(message = "请选择驳回原因")
    private Integer reason;
    @Size(max = 200, message = "说明不能超过 200 字")
    private String note;
}
