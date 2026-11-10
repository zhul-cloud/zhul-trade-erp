package com.zhul.erp.modules.crm.followup.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 批量快速跟进（spec「快速跟进」）：按阶段分组，每个客户各带自己的结果 */
@Data
public class QuickBatchRequest {

    @NotEmpty(message = "请选择客户")
    @Size(max = 50, message = "一次最多选 50 个客户")
    @Valid
    private List<Entry> entries;

    /** 实际沟通日期，所有客户共用，不填为今天 */
    private LocalDate contactDate;

    @NotBlank(message = "缺少幂等键，请刷新后重试")
    @Size(max = 30, message = "幂等键过长")
    private String idempotencyKey;

    @Data
    public static class Entry {
        @NotNull(message = "请选择客户")
        private Long customerId;
        @NotEmpty(message = "请选择单据")
        @Size(max = 500, message = "一个客户最多选 500 张单据")
        private List<Long> itemIds;
        @NotBlank(message = "请选择跟进结果")
        private String result;
    }
}
