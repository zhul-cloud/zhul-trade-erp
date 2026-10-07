package com.zhul.erp.modules.sales.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 关闭 PI：原因取字典「未成交原因」，选「其他」时说明必填 */
@Data
public class ClosePiRequest {
    private String reason;
    @Size(max = 300, message = "说明不能超过 300 字")
    private String note;
    /** 同时把来源报价单标为未成交（默认是） */
    private Boolean markQuotationLost = true;
}
