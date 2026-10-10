package com.zhul.erp.modules.quotation.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 标为未成交 */
@Data
public class MarkLostRequest {
    /** 字典 quotation_lost_reason 的编码 */
    private String reason;
    @Size(max = 300, message = "说明不能超过 300 字")
    private String note;
}
