package com.zhul.erp.modules.masterdata.customerprofile.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 合并客户：sourceId 并入 targetId */
@Data
public class MergeRequest {
    @NotNull(message = "请选择保留的客户")
    private Long targetId;
    @NotNull(message = "请选择被合并的客户")
    private Long sourceId;
}
