package com.zhul.erp.modules.sales.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 标为已发送 / 追加发送记录 */
@Data
public class MarkPiSentRequest {
    @NotNull(message = "请选择发送方式")
    /** 2-Excel、3-PDF、4-图片 */
    private Integer channel;
}
