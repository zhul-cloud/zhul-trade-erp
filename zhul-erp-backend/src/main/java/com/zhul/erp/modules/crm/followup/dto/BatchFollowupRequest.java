package com.zhul.erp.modules.crm.followup.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 批量跟进（spec「批量跟进与批量延后」）：每个客户各写一条跟进记录，作用到所列单据。
 * situation 只在商机成交生效（1-客户给了反馈、2-客户在谈条件、4-没回复）；note 记为跟进摘要。
 */
@Data
public class BatchFollowupRequest {
    @Valid
    @NotEmpty(message = "请选择客户")
    @Size(max = 50, message = "一次最多选 50 个客户")
    private List<Entry> entries;
    private Integer situation;
    @NotBlank(message = "请写下一步要做什么")
    @Size(max = 200, message = "下一步不能超过200个字")
    private String nextAction;
    @NotNull(message = "请选择下一步日期")
    private LocalDate nextActionDate;
    private Integer channel;
    @Size(max = 1000, message = "备注不能超过1000个字")
    private String note;
    @NotBlank(message = "缺少幂等键，请刷新后重试")
    @Size(max = 40, message = "幂等键过长")
    private String idempotencyKey;

    @Data
    public static class Entry {
        @NotNull(message = "请选择客户")
        private Long customerId;
        @NotEmpty(message = "请选择单据")
        @Size(max = 500, message = "一个客户最多选 500 张单据")
        private List<Long> itemIds;
    }
}
