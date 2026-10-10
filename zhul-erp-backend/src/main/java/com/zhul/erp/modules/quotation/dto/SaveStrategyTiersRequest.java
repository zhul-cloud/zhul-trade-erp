package com.zhul.erp.modules.quotation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** 保存「按金额分层毛利」的默认档位（本租户） */
@Data
public class SaveStrategyTiersRequest {
    @NotEmpty(message = "至少要有一档")
    @Valid
    private List<StrategyTierDTO> tiers;
}
