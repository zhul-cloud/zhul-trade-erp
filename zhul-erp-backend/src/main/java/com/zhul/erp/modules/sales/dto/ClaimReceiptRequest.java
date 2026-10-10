package com.zhul.erp.modules.sales.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 认领到账：把一笔未认领到账认领到 PI，可选对应的水单 */
@Data
public class ClaimReceiptRequest {
    @NotNull(message = "请选择要认领的到账")
    private Long receiptId;
    private Long slipId;
}
