package com.zhul.erp.modules.sales.dto;

import lombok.Data;

import java.time.LocalDate;

/** 重新打开 PI：有效期已过时可顺手改有效期 */
@Data
public class ReopenPiRequest {
    private LocalDate validUntil;
}
