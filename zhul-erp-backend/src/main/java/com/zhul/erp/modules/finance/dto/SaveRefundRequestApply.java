package com.zhul.erp.modules.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** 提出退款申请（业务员） */
@Data
public class SaveRefundRequestApply {
    @NotNull(message = "请选择销售订单")
    private Long soId;
    /** 售后单（选填，从订单详情提出时可为空） */
    private Long afterSalesId;
    @NotNull(message = "请填写申请金额")
    @DecimalMin(value = "0.01", message = "申请金额须大于 0")
    private BigDecimal amount;
    @NotBlank(message = "请选择退款原因")
    private String reason;
    /** 建议去向：1-原路退回平台、2-银行汇款、3-转客户余额 */
    @NotNull(message = "请选择建议去向")
    private Integer method;
    @Size(max = 300, message = "客户收款信息不能超过 300 字")
    private String payeeInfo;
    @Size(max = 500, message = "说明不能超过 500 字")
    private String remark;
    /** 分项（选填）：不填时为一行「退款」；填写时合计须等于申请金额 */
    @jakarta.validation.Valid
    private java.util.List<Line> lines;

    @Data
    public static class Line {
        @NotBlank(message = "请填写退款项目")
        @Size(max = 100, message = "退款项目不能超过 100 字")
        private String itemName;
        @NotNull(message = "请填写分项金额")
        @DecimalMin(value = "0.01", message = "分项金额须大于 0")
        private BigDecimal amount;
        @Size(max = 200, message = "说明不能超过 200 字")
        private String note;
    }
}
