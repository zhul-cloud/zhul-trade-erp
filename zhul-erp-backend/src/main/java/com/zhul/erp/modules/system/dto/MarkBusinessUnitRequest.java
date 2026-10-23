package com.zhul.erp.modules.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 标记或取消事业部 */
@Data
public class MarkBusinessUnitRequest {
    @NotNull(message = "请选择是否标记为事业部")
    private Boolean enabled;
    /** 开始核算月份 yyyy-MM（标记时必填） */
    private String startMonth;
}
