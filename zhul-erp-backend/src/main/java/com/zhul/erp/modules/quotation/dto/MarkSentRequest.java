package com.zhul.erp.modules.quotation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 标为已发送 / 追加发送记录 */
@Data
public class MarkSentRequest {
    /** 发送方式（1-文字、2-Excel、3-PDF、4-图片） */
    @NotNull(message = "请选择发送方式")
    private Integer channel;
}
