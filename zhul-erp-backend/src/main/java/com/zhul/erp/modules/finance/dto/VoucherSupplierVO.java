package com.zhul.erp.modules.finance.dto;

import lombok.Data;

/** 科目设置：凭证里出现过的供应商与 T+ 往来单位编码 */
@Data
public class VoucherSupplierVO {
    private Long supplierId;
    private String supplierName;
    private String supplierCode;
    /** T+ 往来单位编码；空则导出用 supplierCode */
    private String partnerCode;
}
