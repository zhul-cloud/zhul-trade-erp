package com.zhul.erp.modules.finance.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 给待报销项补传截图（国内快递、退货运费） */
@Data
public class ProofRequest {
    @NotNull
    private Integer sourceType;
    @NotNull
    private Long sourceId;
    @NotEmpty(message = "请上传截图")
    private List<Long> attachmentIds;
}
