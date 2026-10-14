package com.zhul.erp.modules.finance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 新建或修改费用单 */
@Data
public class SaveExpenseRequest {
    @NotBlank(message = "请填写名称")
    @Size(max = 64, message = "名称不能超过 64 字")
    private String name;
    /** 费用类别；为空时关联订单的为 ORDER、否则为 OTHER */
    private String category;
    /** 分摊月数，默认 1 */
    @jakarta.validation.constraints.Min(value = 1, message = "分摊月数至少 1 个月")
    @jakarta.validation.constraints.Max(value = 36, message = "分摊月数最多 36 个月")
    private Integer amortizeMonths;
    @NotNull(message = "请填写发生日期")
    private LocalDate occurDate;
    /** 1-供应商、2-线上店铺、3-货代、4-其他 */
    @NotNull(message = "请选择收款方")
    private Integer payeeType;
    private Long supplierId;
    /** 线上店铺平台（采购渠道编码） */
    private Integer shopChannel;
    private String shopName;
    /** 其他：收款方名称 */
    private String payeeName;
    @NotBlank(message = "请选择币种")
    private String currencyCode;
    @NotNull(message = "请填写金额")
    @DecimalMin(value = "0.01", message = "金额须大于 0")
    private BigDecimal amount;
    /** 1-公司对公、2-员工垫付 */
    @NotNull(message = "请选择付款方式")
    private Integer method;
    /** 员工垫付的垫付人，默认当前用户 */
    private Long advanceUserId;
    @Size(max = 500)
    private String remark;
    private List<Long> attachmentIds;
    @Valid
    private List<Alloc> allocations;

    @Data
    public static class Alloc {
        @NotNull
        private Long soItemId;
        @NotNull
        @DecimalMin(value = "0.01", message = "分摊金额须大于 0")
        private BigDecimal amount;
    }
}
