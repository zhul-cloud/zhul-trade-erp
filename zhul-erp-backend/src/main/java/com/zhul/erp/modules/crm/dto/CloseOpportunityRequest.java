package com.zhul.erp.modules.crm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 结束商机：result 为 LOST（输单）或 INVALID（无效），都必须给原因；不能手动赢单，赢单只由收款到账自动触发。
 * 输单时 nurture 为 true（前端默认）则该客户商机成交板块的跟进对象转为培育中，nurtureUntil 为空时按系统参数默认天数。
 */
@Data
public class CloseOpportunityRequest {

    @NotBlank(message = "请选择结果")
    private String result;

    private Integer reason;

    @Size(max = 500, message = "说明不能超过500个字符")
    private String note;

    /** 输单后转入培育（只对输单有效） */
    private Boolean nurture;

    /** 培育到期（重新激活）日期，为空时按默认天数 */
    private LocalDate nurtureUntil;
}
