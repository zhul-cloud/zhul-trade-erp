package com.zhul.erp.modules.crm.followup.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/** 开发对象转商机（第四期 design D7）：需求摘要默认取采购信号，首次接触日默认取首次触达日 */
@Data
public class ConvertRequest {
    @Size(max = 500, message = "需求摘要不能超过500个字")
    private String demandSummary;
    private LocalDate firstContactDate;
    /** 商机负责人，不填为客户负责人（没有时为当前用户）；须在当前用户的数据范围内 */
    private Long ownerId;
}
