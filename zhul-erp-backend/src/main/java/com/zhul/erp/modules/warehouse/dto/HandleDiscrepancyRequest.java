package com.zhul.erp.modules.warehouse.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;

/** 处理到货差异 */
@Data
public class HandleDiscrepancyRequest {
    /** 1-等补发、2-不补了、3-退货换货、4-退货不补、5-折价接收、6-退回供应商、7-暂存 */
    @NotNull(message = "请选择处理方式")
    private Integer resolution;
    @Size(max = 300, message = "说明不能超过300个字")
    private String note;
    /** 折价接收：折价金额（采购单币种） */
    @DecimalMin(value = "0", message = "折价金额不能为负数")
    @Digits(integer = 16, fraction = 2, message = "折价金额最多两位小数")
    private BigDecimal discountAmount;
    @Size(max = 32, message = "快递公司不能超过32个字")
    private String returnCarrier;
    @Size(max = 64, message = "快递单号不能超过64个字")
    private String returnTrackingNo;
    @DecimalMin(value = "0", message = "运费不能为负数")
    @Digits(integer = 16, fraction = 2, message = "运费最多两位小数")
    private BigDecimal returnFreight;
    /** 暂存：供应商白送（成本为 0） */
    private Boolean freeOfCharge;
    /** 暂存：位置备注 */
    @Size(max = 100, message = "位置备注不能超过100个字")
    private String locationNote;
}
