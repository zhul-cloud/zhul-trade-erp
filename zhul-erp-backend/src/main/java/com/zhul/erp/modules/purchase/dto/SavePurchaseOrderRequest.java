package com.zhul.erp.modules.purchase.dto;

import com.zhul.erp.modules.masterdata.dto.PaymentTermDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 保存采购单（草稿或已下单）：单头、各行数量与单价、其他费用、付款条件、合同 */
@Data
public class SavePurchaseOrderRequest {
    @NotBlank(message = "请选择币种")
    private String currencyCode;
    @NotNull(message = "请选择是否含税")
    private Boolean taxIncluded;
    @DecimalMin(value = "0", message = "税率不能为负")
    @DecimalMax(value = "100", message = "税率不能超过 100%")
    private BigDecimal taxRate;
    private List<PaymentTermDTO> paymentTerms;
    @Size(max = 64, message = "合同编号不能超过 64 个字符")
    private String contractNo;
    @DecimalMin(value = "0", message = "合同金额不能为负")
    private BigDecimal contractAmount;
    @Valid
    @NotNull(message = "缺少型号")
    private List<Line> items;
    @Valid
    @Size(max = 20, message = "其他费用最多 20 项")
    private List<Fee> fees;

    @Data
    public static class Line {
        @NotNull(message = "缺少行")
        private Long id;
        @NotNull(message = "请填写数量")
        @Min(value = 1, message = "数量须大于 0")
        private Integer quantity;
        @DecimalMin(value = "0", message = "单价不能为负")
        private BigDecimal unitPrice;
    }

    @Data
    public static class Fee {
        @NotBlank(message = "请填写费用名称")
        @Size(max = 64, message = "费用名称不能超过 64 个字符")
        private String feeName;
        @NotNull(message = "请填写费用金额")
        @DecimalMin(value = "0", message = "费用金额不能为负")
        private BigDecimal amount;
    }
}
