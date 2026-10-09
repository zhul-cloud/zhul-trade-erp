package com.zhul.erp.modules.warehouse.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;

/** 处置暂存货 */
@Data
public class HandleHoldRequest {
    /** 2-退回供应商、3-报废、4-转样品 */
    @NotNull(message = "请选择处置方式")
    private Integer status;
    @NotBlank(message = "请填写说明")
    @Size(max = 300, message = "说明不能超过300个字")
    private String note;
    @Size(max = 32, message = "快递公司不能超过32个字")
    private String returnCarrier;
    @Size(max = 64, message = "快递单号不能超过64个字")
    private String returnTrackingNo;
    @DecimalMin(value = "0", message = "运费不能为负数")
    @Digits(integer = 16, fraction = 2, message = "运费最多两位小数")
    private BigDecimal returnFreight;
}
