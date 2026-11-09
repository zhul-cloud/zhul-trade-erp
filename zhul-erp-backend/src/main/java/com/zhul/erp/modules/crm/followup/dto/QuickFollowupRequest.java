package com.zhul.erp.modules.crm.followup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 单个客户快速跟进（spec「快速跟进」）：选一个结果，下一步和日期由系统按阶段填 */
@Data
public class QuickFollowupRequest {

    @NotNull(message = "请选择客户")
    private Long customerId;

    @NotEmpty(message = "请选择单据")
    @Size(max = 500, message = "一次跟进最多选 500 张单据")
    private List<Long> itemIds;

    /** 快速跟进结果编码，见 GET /followups/quick-results */
    @NotBlank(message = "请选择跟进结果")
    private String result;

    /** 实际沟通日期，不填为今天 */
    private LocalDate contactDate;

    @NotBlank(message = "缺少幂等键，请刷新后重试")
    @Size(max = 40, message = "幂等键过长")
    private String idempotencyKey;
}
