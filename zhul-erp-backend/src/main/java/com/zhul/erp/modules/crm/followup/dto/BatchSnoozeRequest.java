package com.zhul.erp.modules.crm.followup.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 批量延后：所选客户的所列单据一起延后到同一天，不算跟进 */
@Data
public class BatchSnoozeRequest {
    @Valid
    @NotEmpty(message = "请选择客户")
    @Size(max = 50, message = "一次最多选 50 个客户")
    private List<BatchFollowupRequest.Entry> entries;
    @NotNull(message = "请选择延后到哪天")
    private LocalDate date;
}
