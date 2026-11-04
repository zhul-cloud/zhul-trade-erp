package com.zhul.erp.modules.crm.followup.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/** 延后：只改提醒日期，不算一次跟进 */
@Data
public class SnoozeRequest {
    @NotNull(message = "请选择延后到哪天")
    private LocalDate date;
}
