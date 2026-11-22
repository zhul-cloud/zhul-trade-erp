package com.zhul.erp.modules.finance.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 设置供应商的 T+ 往来单位编码（空则导出用 ERP 供应商编码） */
@Data
public class SaveVoucherSupplierRequest {
    @Size(max = 32)
    @Pattern(regexp = "^[0-9A-Za-z.\\-]*$", message = "往来单位编码只能是字母、数字、点或横线")
    private String partnerCode;
}
