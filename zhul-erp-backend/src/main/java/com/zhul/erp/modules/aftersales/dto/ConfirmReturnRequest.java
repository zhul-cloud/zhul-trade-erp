package com.zhul.erp.modules.aftersales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 确认退货：退货退款时同时填写退款申请（逐行金额、原因、建议去向、客户收款信息） */
@Data
public class ConfirmReturnRequest {
    @Valid
    private Refund refund;

    @Data
    public static class Refund {
        @Valid
        private List<Line> lines;
        private String reason;
        private Integer method;
        @Size(max = 300, message = "收款信息不能超过 300 字")
        private String payeeInfo;
        @Size(max = 500, message = "说明不能超过 500 字")
        private String remark;
    }

    @Data
    public static class Line {
        /** 销售退货单行 ID */
        @NotNull
        private Long returnItemId;
        @NotNull(message = "请填写退款金额")
        private BigDecimal amount;
    }
}
